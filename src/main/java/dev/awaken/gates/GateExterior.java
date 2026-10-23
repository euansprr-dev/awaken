package dev.awaken.gates;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.progression.Rank;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The surface gate's upkeep (docs/modules/structures.md): gates built before the vortex get the new structure the
 * first time their chunk is loaded, and game masters can stand a showcase gate of any rank and look in front of them
 * ({@code /awaken gate showcase <rank> <look> [red]}, {@code /awaken gate showcase clear}) for screenshots.
 */
public final class GateExterior {
    private GateExterior() {}

    static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(GateExterior::onServerTick);
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("gate").then(Commands.literal("showcase").requires(AwakenCommands.admin())
                .then(Commands.literal("clear").executes(GateExterior::clear))
                .then(Commands.argument("rank", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (String r : List.of("E", "D", "C", "B", "A", "S", "SOVEREIGN")) b.suggest(r);
                            return b.buildFuture();
                        })
                        .executes(c -> showcase(c, "open", false))
                        .then(Commands.argument("look", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String m : GateVortex.MODE_NAMES) b.suggest(m);
                                    return b.buildFuture();
                                })
                                .executes(c -> showcase(c, StringArgumentType.getString(c, "look"), false))
                                .then(Commands.argument("red", BoolArgumentType.bool())
                                        .executes(c -> showcase(c, StringArgumentType.getString(c, "look"), BoolArgumentType.getBool(c, "red")))))))));
        GateExteriorTests.register();
    }

    // ---- gates built before the vortex ---------------------------------------------------------------------------------

    private static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        if (server.getTickCount() % 200 != 37) return;
        upgradeLoaded(server);
    }

    /** Rebuilds every loaded surface gate that still has the old doorway. Returns how many it rebuilt. */
    static int upgradeLoaded(MinecraftServer server) {
        ServerLevel over = server.overworld();
        int n = 0;
        for (GateRecord g : List.copyOf(GateRegistry.get(server).all())) {
            if (!g.structurePlaced || g.state == GateState.RELEASED || g.state == GateState.BUILDING) continue;
            if (!over.isLoaded(Overworld.anchor(g.pos)) || !over.isLoaded(g.pos.offset(16, 0, 16)) || !over.isLoaded(g.pos.offset(-16, 0, -16))) continue;
            if (!Overworld.legacy(over, g)) continue;
            Overworld.sweepLegacy(over, g, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            Overworld.place(over, g, g.state == GateState.BROKEN);
            g.trace.add("surface rebuilt as a vortex");
            GateRegistry.get(server).setDirty();
            Awaken.LOG.info("[gates] gate {} rebuilt as a vortex at {}", g.id, g.pos);
            n++;
        }
        return n;
    }

    // ---- showcase gates (game masters) ---------------------------------------------------------------------------------

    /** A stand-in record for a showcase: never registered, so no Director, break or run ever sees it. */
    static GateRecord display(BlockPos ground, int axis, int rank, boolean red) {
        Rank r = Rank.values()[GateVortex.clampRank(rank)];
        GatePlan plan = new GatePlan(ground.asLong(), GatePlan.CONTENT_VERSION, r, Theme.BELL_CRYPT, Layout.LABYRINTH, List.of(),
                red ? Twist.RED_GATE : Twist.NONE, 3, Theme.BELL_CRYPT.bossId(), Theme.HIVE, 0);
        return new GateRecord(-1, plan, ground, axis, -1, 0, 99, GateState.READY, Optional.empty());
    }

    /** Builds a showcase gate on the ground at {@code ground} with a fixed look. */
    public static void standShowcase(ServerLevel level, BlockPos ground, int axis, int rank, int mode, boolean red) {
        GateRecord g = display(ground, axis, rank, red);
        Overworld.place(level, g, mode == GateVortex.BROKEN);
        if (level.getBlockEntity(Overworld.anchor(ground)) instanceof GateVortexBlockEntity be) {
            be.showcase(rank, axis, mode, red, mode == GateVortex.WARNING ? 1F : 0F, level.getGameTime());
        }
    }

    private static int showcase(CommandContext<CommandSourceStack> c, String look, boolean red) {
        CommandSourceStack src = c.getSource();
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        int rank = rankOf(StringArgumentType.getString(c, "rank"));
        int mode = GateVortex.modeByName(look.toLowerCase(Locale.ROOT));
        if (rank < 0 || mode < 0) {
            src.sendFailure(Component.literal("Rank E D C B A S SOVEREIGN; look " + String.join(" ", GateVortex.MODE_NAMES)));
            return 0;
        }
        ServerLevel level = p.level();
        // stand it in front of the player, the opening facing them, far enough to see the whole thing
        GateVortex.Size s = GateVortex.size(rank);
        double dist = s.ringRadius() + 8 + s.ry();
        float yaw = p.getYRot() * (float) (Math.PI / 180.0);
        double dx = -Math.sin(yaw), dz = Math.cos(yaw);
        int x = (int) Math.floor(p.getX() + dx * dist), z = (int) Math.floor(p.getZ() + dz * dist);
        int axis = Math.abs(dx) > Math.abs(dz) ? 1 : 0;
        BlockPos ground = new BlockPos(x, Overworld.groundY(level, x, z), z);
        standShowcase(level, ground, axis, rank, mode, red);
        src.sendSuccess(() -> Component.literal("Showcase " + GateVortex.MODE_NAMES[mode] + " " + Rank.values()[rank].label + "-rank gate at "
                + ground.toShortString() + " (/awaken gate showcase clear removes it)"), true);
        return 1;
    }

    static int rankOf(String s) {
        String u = s.toUpperCase(Locale.ROOT);
        if (u.startsWith("SOV")) return 6;
        return switch (u) {
            case "E" -> 0;
            case "D" -> 1;
            case "C" -> 2;
            case "B" -> 3;
            case "A" -> 4;
            case "S" -> 5;
            default -> -1;
        };
    }

    /** Removes every showcase gate within 96 blocks. */
    private static int clear(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return 0;
        int n = clearShowcases(p.level(), p.blockPosition(), 96);
        c.getSource().sendSuccess(() -> Component.literal(n + " showcase gate(s) removed"), true);
        return n;
    }

    static int clearShowcases(ServerLevel level, BlockPos near, int radius) {
        List<GateVortexBlockEntity> found = new ArrayList<>();
        ChunkPos c0 = ChunkPos.containing(near);
        int cr = (radius >> 4) + 1;
        for (int cx = c0.x() - cr; cx <= c0.x() + cr; cx++) {
            for (int cz = c0.z() - cr; cz <= c0.z() + cr; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof GateVortexBlockEntity v && v.gateId() < 0) found.add(v);
                }
            }
        }
        for (GateVortexBlockEntity v : found) {
            GateRecord g = display(v.getBlockPos().below(), v.axis(), v.rank(), v.red());
            Overworld.remove(level, g);
        }
        return found.size();
    }
}
