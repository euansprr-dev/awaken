package dev.awaken.above;

import dev.awaken.above.halo.Halo;
import dev.awaken.above.ruin.RuinKind;
import dev.awaken.above.ruin.RuinLocator;
import dev.awaken.core.AwakenCommands;
import dev.awaken.progression.Progression;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.List;

/** /awaken above ruin locate · /awaken admin blueprint all · /awaken admin halo */
final class AboveCommands {
    private AboveCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            root.then(Commands.literal("above").requires(AwakenCommands.admin())
                    .then(Commands.literal("ruin").then(Commands.literal("locate").executes(c -> {
                        var src = c.getSource();
                        ServerLevel level = src.getLevel();
                        if (level.dimension() != Level.OVERWORLD) {
                            src.sendFailure(Component.literal("Precursor ruins are in the overworld."));
                            return 0;
                        }
                        ServerPlayer sp = src.getPlayer();
                        var origin = net.minecraft.core.BlockPos.containing(src.getPosition());
                        ChunkPos spawn = RuinLocator.spawnVault(level);
                        if (spawn != null) {
                            var p = RuinLocator.center(spawn, origin.getY());
                            src.sendSuccess(() -> line("Spawn vault", p, origin, sp != null && Above.data(sp).hasRuinNear(p, 48)), false);
                        }
                        int n = 0;
                        for (RuinKind k : List.of(RuinKind.VAULT, RuinKind.SKY, RuinKind.OCEAN, RuinKind.MONOLITH)) {
                            var found = RuinLocator.near(level, origin, 4000, List.of(k), sp == null ? null : Above.data(sp), false, 1, 96);
                            if (found.isEmpty()) {
                                src.sendSuccess(() -> Component.literal(k.displayName + ": none within 4,000 blocks").withStyle(ChatFormatting.GRAY), false);
                                continue;
                            }
                            var f = found.get(0);
                            src.sendSuccess(() -> line(k.displayName, f.pos(), origin, f.discovered()), false);
                            n++;
                        }
                        return n;
                    }))));
            var admin = Commands.literal("admin").requires(AwakenCommands.admin());
            admin.then(Commands.literal("blueprint").then(Commands.literal("all").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                for (MachineType t : MachineType.values()) Above.learn(sp, t);
                c.getSource().sendSuccess(() -> Component.literal("All " + MachineType.values().length + " blueprints learned"), true);
                return MachineType.values().length;
            })));
            admin.then(Commands.literal("halo").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Halo.reveal(sp);
                if (Progression.profile(sp).counter(Above.HALO_SEEN) == 0) Progression.count(sp, Above.HALO_SEEN, 1);
                return Halo.send(sp) ? 1 : 0;
            }));
            root.then(admin);
        });
    }

    private static Component line(String name, net.minecraft.core.BlockPos p, net.minecraft.core.BlockPos from, boolean found) {
        int dist = (int) Math.sqrt(p.distSqr(new net.minecraft.core.BlockPos(from.getX(), p.getY(), from.getZ())));
        String tp = "/tp @s " + p.getX() + " ~ " + p.getZ();
        return Component.literal(name + ": " + p.getX() + ", " + p.getZ() + " (" + dist + " blocks)" + (found ? " · found" : ""))
                .withStyle(s -> s.withColor(found ? ChatFormatting.GREEN : ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.SuggestCommand(tp))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(tp))));
    }
}
