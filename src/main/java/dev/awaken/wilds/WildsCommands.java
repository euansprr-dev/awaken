package dev.awaken.wilds;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.api.ArrivalHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.HorizonHooks;
import dev.awaken.core.api.WardHooks;
import dev.awaken.progression.Progression;
import dev.awaken.wilds.arrival.Arrival;
import dev.awaken.wilds.ward.WardEvents;
import dev.awaken.wilds.ward.Wards;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /awaken wilds why} (anyone): the ward you stand in and whether its rules hold here, how the last 20 seconds
 * read and the verdict a chest would see, the Horizon line and the sites behind it, and your biome count. For game
 * masters' tours: {@code place} (the held block against what you look at, as you) and {@code strike} (a pick's blow).
 */
final class WildsCommands {
    private WildsCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var w = Commands.literal("wilds");
            w.then(Commands.literal("why").executes(WildsCommands::why));
            // tours: what a survival player placing and mining at a site sets off, through the same paths
            w.then(Commands.literal("place").requires(AwakenCommands.admin()).executes(c -> place(c.getSource().getPlayerOrException())));
            w.then(Commands.literal("strike").requires(AwakenCommands.admin()).executes(c -> strike(c.getSource().getPlayerOrException())));
            root.then(w);
        });
    }

    /** Places the held block (else stone) against the face you look at, as you: inside a ward it crumbles. */
    private static int place(ServerPlayer p) {
        if (!(p.pick(5, 0, false) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return 0;
        BlockPos at = hit.getBlockPos().relative(hit.getDirection());
        BlockState state = p.getMainHandItem().getItem() instanceof BlockItem bi ? bi.getBlock().defaultBlockState() : Blocks.STONE.defaultBlockState();
        if (!p.level().getBlockState(at).canBeReplaced()) return 0;
        p.level().setBlock(at, state, 3);
        boolean queued = WardEvents.placed(p, p.level(), at, state);
        p.sendSystemMessage(Component.literal(queued ? "Placed; the ward will take it back." : "Placed; no ward holds here (or you're in creative)."));
        return queued ? 1 : 0;
    }

    /** Strikes the block you look at, as a pick would: a site's wall inside a ward holds. */
    private static int strike(ServerPlayer p) {
        if (!(p.pick(5, 0, false) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return 0;
        boolean held = WardEvents.refuses(p, p.level(), hit.getBlockPos(), p.level().getBlockState(hit.getBlockPos()));
        if (!held) p.sendSystemMessage(Component.literal("Nothing holds it: not a site's wall, no ward, or you're in creative."));
        return held ? 1 : 0;
    }

    private static int why(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var level = p.level();
        var ward = WardHooks.at(level, p.blockPosition());
        String wardLine = ward.map(w -> "Ward: " + w.id() + " (" + w.kind() + ")"
                + (!Wards.on() ? ", wards are off" : HearthHooks.inside(level, p.blockPosition()) ? ", but this is a Hearth: its rules don't hold" : ", its rules hold here"))
                .orElse("Ward: none here");
        say(c, wardLine);
        List<ArrivalHooks.Way> h = ArrivalHooks.history(p);
        Map<ArrivalHooks.Way, Integer> tally = new EnumMap<>(ArrivalHooks.Way.class);
        for (ArrivalHooks.Way w : h) tally.merge(w, 1, Integer::sum);
        StringBuilder b = new StringBuilder("Last ").append(h.size() / 4).append(" s:");
        tally.forEach((w, n) -> b.append(' ').append(w.name().toLowerCase()).append(' ').append(n * 100 / Math.max(1, h.size())).append('%'));
        say(c, b.toString());
        String visit = Arrival.describeVisit(p);
        say(c, "Arrived " + (ArrivalHooks.byMovement(p) ? "by movement" : "by building") + (visit == null ? "" : " (visit " + visit + ")"));
        var sites = HorizonHooks.sites(p);
        var line = HorizonHooks.collect(p);
        say(c, "Horizon: " + line.map(l -> (l.tease() ? "tease · " : "") + l.name() + (l.need().isEmpty() ? "" : " · needs " + l.need())).orElse("nothing")
                + " (" + sites.size() + " site(s) known)");
        say(c, "Biomes discovered: " + Progression.profile(p).counter(BiomeDiscovery.COUNTER));
        return 1;
    }

    private static void say(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendSuccess(() -> Component.literal(s), false);
    }
}
