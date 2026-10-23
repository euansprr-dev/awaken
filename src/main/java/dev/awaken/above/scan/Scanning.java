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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

/**
 * What the Scanner does: read broken machines and log terminals, and listen to the sky for undiscovered ruins (and, via
 * {@link #addEchoSource}, other landmarks such as the Well; ruins the player already walked into are skipped through
 * {@link #setVisitedRuins}).
 */
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

    private record OmenCache(long builtAt, BlockPos builtFrom, List<Heard> heard) {}

    private static final Map<UUID, OmenCache> OMENS = new HashMap<>();
    private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

    public static void forget(UUID player) {
        OMENS.remove(player);
        LAST_HINT.remove(player);
    }

    // ---- other landmarks (hook for the world module's landmarks: the Well, ruins already visited) ----------------------

    /** Something besides the ruins the Scanner can hear (another module's landmark, e.g. the Well). */
    public record Echo(BlockPos pos, String omen) {}

    @FunctionalInterface
    public interface EchoSource {
        List<Echo> echoes(ServerPlayer player, int range);
    }

    /** What a sweep can point at, nearest first: a ruin's omen or another landmark's echo. */
    public record Heard(BlockPos pos, String omen, double distance) {}

    private static final List<EchoSource> ECHO_SOURCES = new CopyOnWriteArrayList<>();
    private static volatile BiPredicate<ServerPlayer, BlockPos> visitedRuin = (p, pos) -> false;

    /** Adds landmarks the sky sweep can point at beside the ruins. */
    public static void addEchoSource(EchoSource source) {
        ECHO_SOURCES.add(source);
    }

    /** Ruins (by the locator's approximate centre) the player already reached in person: the sweep skips them too. */
    public static void setVisitedRuins(BiPredicate<ServerPlayer, BlockPos> visited) {
        visitedRuin = visited;
    }

    /** The (up to four) nearest things this player's Scanner hears, nearest first. Cached for a few seconds. */
    public static List<Heard> heard(ServerPlayer player) {
        long now = player.level().getGameTime();
        OmenCache c = OMENS.get(player.getUUID());
        if (c == null || now - c.builtAt() > 400 || c.builtFrom().distSqr(player.blockPosition()) > 96 * 96) {
            List<RuinLocator.Found> found = RuinLocator.near(player.level(), player.blockPosition(), OMEN_RANGE,
                    List.of(RuinKind.values()), Above.data(player), true, 8);
            // an empty answer may only mean the locator hasn't finished sampling this area: ask again soon
            c = new OmenCache(found.size() < 2 ? now - 360 : now, player.blockPosition(), collect(player, found));
            OMENS.put(player.getUUID(), c);
        }
        return c.heard();
    }

    static List<Heard> collect(ServerPlayer player, List<RuinLocator.Found> found) {
        List<Heard> out = new ArrayList<>();
        for (RuinLocator.Found f : found) if (!visitedRuin.test(player, f.pos())) out.add(new Heard(f.pos(), f.kind().omen, f.distance()));
        for (EchoSource s : ECHO_SOURCES) {
            try {
                for (Echo e : s.echoes(player, OMEN_RANGE)) {
                    double dx = e.pos().getX() + 0.5 - player.getX(), dz = e.pos().getZ() + 0.5 - player.getZ();
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d <= OMEN_RANGE) out.add(new Heard(e.pos(), e.omen(), d));
                }
            } catch (RuntimeException ex) {
                dev.awaken.Awaken.LOG.warn("[scanner] an echo source failed: {}", ex.toString());
            }
        }
        out.sort(Comparator.comparingDouble(Heard::distance));
        return out.size() > 4 ? List.copyOf(out.subList(0, 4)) : out;
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
        List<Heard> found = heard(player);
        if (found.isEmpty()) {
            if (usedTicks % 40 == 10) {
                player.sendOverlayMessage(Component.literal("Static. Nothing of theirs within earshot."));
                ping(player, AboveSounds.PING, 0.25f, 0.5f);
            }
            return;
        }
        double yaw = Math.toRadians(player.getYRot());
        double lookX = -Math.sin(yaw), lookZ = Math.cos(yaw);
        Heard best = null;
        double bestAlign = -2;
        for (Heard f : found) {
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
            player.sendOverlayMessage(Component.literal("“" + best.omen() + "” · " + distanceWord(best.distance()) + ", "
                    + Travel.compass(best.pos().getX() - player.getBlockX(), best.pos().getZ() - player.getBlockZ())));
            long now = player.level().getGameTime();
            if (now - LAST_HINT.getOrDefault(player.getUUID(), -100_000L) > 20 * 60) {
                LAST_HINT.put(player.getUUID(), now);
                DirectorLog.log(player, "scanner", "omen \"" + best.omen() + "\" " + (int) best.distance() + " blocks");
            }
        } else if (bestAlign > 0.6) {
            player.sendOverlayMessage(Component.literal("A faint echo… closer to it."));
        } else {
            player.sendOverlayMessage(Component.literal("Sweep the sky slowly."));
        }
    }
}
