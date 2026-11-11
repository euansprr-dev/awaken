package dev.awaken.above;

import dev.awaken.above.halo.Halo;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.awaken.above.ruin.RuinCensus;
import dev.awaken.above.ruin.RuinKind;
import dev.awaken.above.ruin.RuinLocator;
import dev.awaken.above.ruin.RuinSpacing;
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

/** /awaken above ruin locate · /awaken above ruin spacing [census [radius] | old | new] · /awaken admin blueprint all · /awaken admin halo */
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
                        for (RuinKind k : List.of(RuinKind.VAULT, RuinKind.SKY, RuinKind.SPIRE, RuinKind.OCEAN, RuinKind.MONOLITH)) {
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
                    }))
                    // room between the blue rope sites (SPEC §17.11): the numbers both ways, and the seam check's switch
                    .then(Commands.literal("spacing")
                            .executes(c -> {
                                c.getSource().sendSuccess(() -> Component.literal("Spacing between Tether Spires and sky platforms: "
                                        + (RuinSpacing.on() ? "on (" + RuinSpacing.SPIRE_APART + " between spires, " + RuinSpacing.SKY_APART
                                        + " between platforms, " + RuinSpacing.SPIRE_FROM_SKY + " from a spire to a platform)" : "OFF for new chunks (today's rule alone)")), false);
                                return RuinSpacing.on() ? 1 : 0;
                            })
                            .then(Commands.literal("census").executes(c -> census(c.getSource(), 6000))
                                    .then(Commands.argument("radius", IntegerArgumentType.integer(1800, 12000))
                                            .executes(c -> census(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))
                            .then(Commands.literal("goto")
                                    .then(Commands.literal("passed").executes(c -> spacingGoto(c.getSource(), false)))
                                    .then(Commands.literal("kept").executes(c -> spacingGoto(c.getSource(), true))))
                            .then(Commands.literal("verify")
                                    .then(Commands.argument("spots", IntegerArgumentType.integer(1, 60)).executes(c -> {
                                        var lines = dev.awaken.above.ruin.RuinCensus.verify(c.getSource().getLevel(),
                                                net.minecraft.core.BlockPos.containing(c.getSource().getPosition()), IntegerArgumentType.getInteger(c, "spots"));
                                        for (String l : lines) c.getSource().sendSuccess(() -> Component.literal(l).withStyle(ChatFormatting.AQUA), false);
                                        return lines.size() == 1 ? 1 : 0;
                                    })))
                            .then(Commands.literal("old").executes(c -> spacing(c.getSource(), false)))
                            .then(Commands.literal("new").executes(c -> spacing(c.getSource(), true))))));
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

    private static int census(net.minecraft.commands.CommandSourceStack src, int radius) {
        if (src.getLevel().dimension() != Level.OVERWORLD) {
            src.sendFailure(Component.literal("Precursor ruins are in the overworld."));
            return 0;
        }
        var r = RuinCensus.count(src.getLevel(), net.minecraft.core.BlockPos.containing(src.getPosition()), radius);
        for (String l : r.lines()) src.sendSuccess(() -> Component.literal(l).withStyle(ChatFormatting.AQUA), false);
        return r.real().offGrid() == 0 && r.real().whole() == r.real().checked() ? 1 : 0;
    }

    /** Dev only, for the seam check: new chunks follow today's rule alone (old) or the spacing too (new). Not saved. */
    private static int spacing(net.minecraft.commands.CommandSourceStack src, boolean on) {
        RuinSpacing.set(on);
        dev.awaken.Awaken.LOG.info("[above] spacing {} for new chunks", on ? "on" : "OFF");
        src.sendSuccess(() -> Component.literal(on ? "New chunks keep the blue rope sites apart." : "New chunks follow today's rule alone (until the server stops)."), true);
        return 1;
    }

    /** Dev only: 70 blocks east of the nearest Tether Spire spot the rule passes over (or keeps), looking at its middle. */
    private static int spacingGoto(net.minecraft.commands.CommandSourceStack src, boolean kept) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        ServerLevel level = src.getServer().overworld();
        var at = RuinCensus.nearestSpire(level, p.blockPosition(), 3000, kept);
        if (at == null) {
            src.sendFailure(Component.literal("No Tether Spire spot within 3,000 blocks that the rule " + (kept ? "keeps" : "passes over")));
            return 0;
        }
        int cx = at.getMinBlockX() + dev.awaken.above.ruin.RuinBuilder.SPIRE_C, cz = at.getMinBlockZ() + dev.awaken.above.ruin.RuinBuilder.SPIRE_C;
        int ground = level.getChunkSource().getGenerator().getFirstOccupiedHeight(cx, cz, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                level, level.getChunkSource().randomState());
        double x = cx + 70.5, y = ground + 30, z = cz + 0.5;
        p.teleportTo(level, x, y, z, java.util.Set.of(), 90F, 0F, true);
        dev.awaken.Awaken.LOG.info("[above] spacing goto: the spire spot at chunk {} (column {}, {}, ground {}) that the rule {}; spacing {}",
                at, cx, cz, ground, kept ? "keeps" : "passes over", RuinSpacing.on() ? "on" : "OFF");
        src.sendSuccess(() -> Component.literal("The Tether Spire spot at " + cx + ", " + cz + " (" + (kept ? "kept" : "passed over by the rule") + ")"), false);
        return 1;
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
