package dev.awaken.above.scan;

import dev.awaken.above.Above;
import dev.awaken.above.AboveData;
import dev.awaken.above.AboveSounds;
import dev.awaken.above.MachineType;
import dev.awaken.above.block.BrokenMachineBlock;
import dev.awaken.above.block.LogTerminalBlock;
import dev.awaken.above.logs.PrecursorLogs;
import dev.awaken.above.machine.Travel;
import dev.awaken.above.ruin.PrecursorRuinStructure;
import dev.awaken.above.ruin.RuinKind;
import dev.awaken.above.ruin.RuinLocator;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.DirectorLog;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the Scanner does: read broken machines and log terminals, and listen to the sky for undiscovered ruins. */
public final class Scanning {
    private Scanning() {}

    /** How far the sky omens reach. */
    public static final int OMEN_RANGE = 2500;

    public static void ping(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS, player.getX(), player.getEyeY(), player.getZ(),
                volume, pitch, player.getRandom().nextLong()));
    }

    // ---- broken machines ------------------------------------------------------------------------------------------

    /** Reads a broken machine. Returns true if it was a fresh (unscanned) machine. */
    public static boolean scanMachine(ServerPlayer player, BlockPos pos, BlockState state) {
        if (!AwakenConfig.ABOVE.get()) return false;
        MachineType type = state.getValue(BrokenMachineBlock.MACHINE);
        ServerLevel level = player.level();
        recordRuin(player, pos);
        if (state.getValue(BrokenMachineBlock.SCANNED)) {
            player.sendOverlayMessage(Component.literal("Already read: " + type.displayName + ". Only salvage now."));
            ping(player, AboveSounds.SCAN_FAIL, 0.6f, 1.2f);
            return false;
        }
        level.setBlock(pos, state.setValue(BrokenMachineBlock.SCANNED, true), Block.UPDATE_ALL);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.1);
        level.playSound(null, pos, AboveSounds.SCAN.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        Progression.count(player, Above.MACHINES_SCANNED, 1);
        boolean fresh = Above.learn(player, type);
        // Every Precursor machine carries its power source's pattern: the first read also teaches the reactor.
        boolean reactor = type != MachineType.REACTOR && Above.learn(player, MachineType.REACTOR);
        SignalWindow.Builder w = SignalWindow.builder(SignalWindow.Kind.NOTICE,
                Component.literal(fresh ? "Blueprint read: " + type.displayName : type.displayName));
        if (fresh) {
            w.body(Component.literal(type.blurb));
            if (reactor) w.row(Component.literal("Also learned"), Component.literal("Essence Reactor (it powers them all)"));
            w.footer(Component.literal("Build it at a Fabricator · Codex updated"));
        } else {
            w.body(Component.literal("You already know this one. The rest of it is salvage."));
        }
        Signal.send(player, w.ttl(20 * 8).priority(fresh ? 4 : 1).build());
        DirectorLog.log(player, "scanner", "read " + type.id + (fresh ? " (new blueprint)" : ""));
        return true;
    }

    // ---- log terminals --------------------------------------------------------------------------------------------

    public static void readTerminal(ServerPlayer player, BlockPos pos, BlockState state) {
        if (!AwakenConfig.ABOVE.get()) return;
        recordRuin(player, pos);
        PrecursorLogs.read(player, state.getValue(LogTerminalBlock.POOL));
    }

    // ---- ruins ----------------------------------------------------------------------------------------------------

    /** If {@code pos} is inside a Precursor ruin, remember the ruin (pylon destination, no more omens for it). */
    public static void recordRuin(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, Above.RUINS_TAG);
        if (!start.isValid() || !(start.getStructure() instanceof PrecursorRuinStructure prs)) return;
        BlockPos center = start.getBoundingBox().getCenter();
        if (Above.addRuin(player, new AboveData.RuinMark(prs.kind().id, center, GlobalPos.of(level.dimension(), player.blockPosition())))) {
            Progression.markNewThing(player, "ruin:" + prs.kind().id);
            Signal.toast(player, Component.literal(prs.kind().displayName + " found · Waypoint Pylons can bring you back"));
            OMENS.remove(player.getUUID());
        }
    }

    // ---- sky omens ------------------------------------------------------------------------------------------------

    private record OmenCache(long builtAt, BlockPos builtFrom, List<RuinLocator.Found> found) {}

    private static final Map<UUID, OmenCache> OMENS = new HashMap<>();
    private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

    public static void forget(UUID player) {
        OMENS.remove(player);
        LAST_HINT.remove(player);
    }

    private static List<RuinLocator.Found> omens(ServerPlayer player) {
        long now = player.level().getGameTime();
        OmenCache c = OMENS.get(player.getUUID());
        if (c == null || now - c.builtAt() > 400 || c.builtFrom().distSqr(player.blockPosition()) > 96 * 96) {
            List<RuinLocator.Found> found = RuinLocator.near(player.level(), player.blockPosition(), OMEN_RANGE,
                    List.of(RuinKind.values()), Above.data(player), true, 4);
            // an empty answer may only mean the locator hasn't finished sampling this area: ask again soon
            c = new OmenCache(found.size() < 2 ? now - 360 : now, player.blockPosition(), found);
            OMENS.put(player.getUUID(), c);
        }
        return c.found();
    }

    static String distanceWord(double d) {
        if (d < 300) return "close";
        if (d < 900) return "a walk away";
        if (d < 1800) return "far";
        return "very far";
    }

    /** Called every 10 ticks while the Scanner is held up. Looking at the sky turns it into a direction finder. */
    public static void sweep(ServerPlayer player, int usedTicks) {
        if (!AwakenConfig.ABOVE.get()) return;
        if (player.getXRot() > -20f) {
            if (usedTicks == 10) player.sendOverlayMessage(Component.literal("Point it at the sky to listen for Precursor echoes."));
            return;
        }
        List<RuinLocator.Found> found = omens(player);
        if (found.isEmpty()) {
            if (usedTicks % 40 == 10) {
                player.sendOverlayMessage(Component.literal("Static. Nothing of theirs within earshot."));
                ping(player, AboveSounds.PING, 0.25f, 0.5f);
            }
            return;
        }
        double yaw = Math.toRadians(player.getYRot());
        double lookX = -Math.sin(yaw), lookZ = Math.cos(yaw);
        RuinLocator.Found best = null;
        double bestAlign = -2;
        for (RuinLocator.Found f : found) {
            double dx = f.pos().getX() + 0.5 - player.getX(), dz = f.pos().getZ() + 0.5 - player.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            double align = len < 1 ? 1 : (dx * lookX + dz * lookZ) / len;
            if (align > bestAlign) {
                bestAlign = align;
                best = f;
            }
        }
        if (best == null) return;
        if (bestAlign > 0.3) ping(player, AboveSounds.PING, 0.35f + 0.35f * (float) bestAlign, 0.6f + 1.2f * (float) Math.max(0, bestAlign));
        if (bestAlign > 0.95) {
            player.sendOverlayMessage(Component.literal("“" + best.kind().omen + "” · " + distanceWord(best.distance()) + ", "
                    + Travel.compass(best.pos().getX() - player.getBlockX(), best.pos().getZ() - player.getBlockZ())));
            long now = player.level().getGameTime();
            if (now - LAST_HINT.getOrDefault(player.getUUID(), -100_000L) > 20 * 60) {
                LAST_HINT.put(player.getUUID(), now);
                DirectorLog.log(player, "scanner", "omen " + best.kind().id + " " + (int) best.distance() + " blocks");
            }
        } else if (bestAlign > 0.6) {
            player.sendOverlayMessage(Component.literal("A faint echo… closer to it."));
        } else {
            player.sendOverlayMessage(Component.literal("Sweep the sky slowly."));
        }
    }
}
