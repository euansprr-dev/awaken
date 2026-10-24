package dev.awaken.compat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.Awaken;
import dev.awaken.above.AboveBlocks;
import dev.awaken.above.MachineType;
import dev.awaken.above.machine.MachineBlockEntity;
import dev.awaken.core.AwakenCommands;
import dev.awaken.gates.GateVortexBlockEntity;
import dev.awaken.gates.Gates;
import dev.awaken.hearth.town.TownNpc;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Set;

/**
 * {@code /awaken compat ...} (game masters).
 * <ul>
 *   <li>{@code discovered [check|reset|all]}: the spoiler guard's record for you. {@code check} applies the rule now,
 *       {@code reset} forgets everything (a fresh player for tours), {@code all} marks every Awaken item found.</li>
 *   <li>Tour helpers for look-at shots: {@code stand gate} (eight blocks in front of the nearest gate, facing its
 *       opening), {@code stand townsfolk} (in front of the nearest townsperson), {@code place <machine>} (builds that
 *       Precursor machine, yours, three blocks ahead; a reactor comes charged).</li>
 * </ul>
 * Each logs a {@code [compat]} line the autopilot can read.
 */
final class CompatCommands {
    private CompatCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var d = Commands.literal("discovered").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                int n = Discovered.of(sp).size();
                c.getSource().sendSuccess(() -> Component.literal(n + " Awaken items discovered"), false);
                Awaken.LOG.info("[compat] {} has discovered {} Awaken items", sp.getScoreboardName(), n);
                return n;
            });
            d.then(Commands.literal("check").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                int added = Discovered.check(sp);
                int n = Discovered.of(sp).size();
                c.getSource().sendSuccess(() -> Component.literal(added + " new, " + n + " discovered"), false);
                Awaken.LOG.info("[compat] discovered check: {} new, {} total", added, n);
                return added;
            }));
            d.then(Commands.literal("reset").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Discovered.reset(sp);
                c.getSource().sendSuccess(() -> Component.literal("Discoveries forgotten"), false);
                Awaken.LOG.info("[compat] discoveries reset for {}", sp.getScoreboardName());
                return 1;
            }));
            d.then(Commands.literal("all").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                int added = Discovered.all(sp);
                c.getSource().sendSuccess(() -> Component.literal(added + " items marked discovered"), false);
                Awaken.LOG.info("[compat] discovered all: {} added", added);
                return added;
            }));
            var stand = Commands.literal("stand");
            stand.then(Commands.literal("gate").executes(CompatCommands::standGate));
            stand.then(Commands.literal("townsfolk").executes(CompatCommands::standTownsfolk));
            var place = Commands.literal("place").then(Commands.argument("machine", StringArgumentType.word())
                    .executes(c -> place(c, StringArgumentType.getString(c, "machine"))));
            root.then(Commands.literal("compat").requires(AwakenCommands.admin()).then(d).then(stand).then(place));
        });
    }

    /** Eight blocks out from the nearest gate's opening, looking into it. */
    private static int standGate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level();
        var gate = Gates.near(level, sp.blockPosition(), 160).stream()
                .min(Comparator.comparingDouble(g -> g.pos().distSqr(sp.blockPosition()))).orElse(null);
        if (gate == null) {
            c.getSource().sendFailure(Component.literal("No gate within 160 blocks"));
            return 0;
        }
        // axis 0: the opening faces +-Z (Overworld's convention); the anchor core's block entity knows it
        int axis = level.getBlockEntity(gate.pos().above()) instanceof GateVortexBlockEntity v ? v.axis() : 0;
        BlockPos g = gate.pos();
        double x = g.getX() + 0.5, z = g.getZ() + 0.5;
        float yaw;
        if (axis == 0) { z -= 8; yaw = 0; } else { x -= 8; yaw = -90; }
        int y = g.getY() + 1;
        sp.teleportTo(level, x, y, z, Set.of(), yaw, -12, true);
        Awaken.LOG.info("[compat] standing at gate {} ({}-rank, {}), axis {}", gate.id(), gate.rank().label, gate.state(), axis);
        return gate.id();
    }

    /** Three blocks in front of the nearest townsperson, facing them. */
    private static int standTownsfolk(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level();
        var npc = level.getEntitiesOfClass(TownNpc.class, new AABB(sp.blockPosition()).inflate(96)).stream()
                .min(Comparator.comparingDouble(n -> n.distanceToSqr(sp))).orElse(null);
        if (npc == null) {
            c.getSource().sendFailure(Component.literal("No townsfolk within 96 blocks"));
            return 0;
        }
        Vec3 at = npc.position();
        Vec3 look = Vec3.directionFromRotation(0, npc.getYRot());
        Vec3 stand = at.add(look.scale(3));
        double dx = at.x - stand.x, dz = at.z - stand.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        sp.teleportTo(level, stand.x, at.y, stand.z, Set.of(), yaw, 8, true);
        npc.setNoAi(true);
        Awaken.LOG.info("[compat] standing at {} ({})", npc.kind(), npc.getName().getString());
        return 1;
    }

    /** A Precursor machine three blocks ahead, owned by you; a reactor gets 4,500 essence. */
    private static int place(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level();
        MachineType t = MachineType.byId(id);
        var block = id.equals("fabricator") ? AboveBlocks.FABRICATOR.get() : t == null ? null : AboveBlocks.MACHINES.get(t).get();
        if (block == null) {
            c.getSource().sendFailure(Component.literal("No machine " + id));
            return 0;
        }
        Vec3 ahead = Vec3.directionFromRotation(0, sp.getYRot()).scale(3);
        BlockPos at = BlockPos.containing(sp.position().add(ahead));
        level.setBlockAndUpdate(at, block.defaultBlockState());
        if (level.getBlockEntity(at) instanceof MachineBlockEntity be) {
            be.setOwner(sp.getUUID());
            if (be.isReactor()) be.addEssence(4500);
        }
        Awaken.LOG.info("[compat] placed {} at {}", id, at.toShortString());
        return 1;
    }
}
