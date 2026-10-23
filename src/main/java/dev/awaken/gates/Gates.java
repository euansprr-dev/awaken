package dev.awaken.gates;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.gates.run.GateMemory;
import dev.awaken.gates.run.RunState;
import dev.awaken.gates.run.Runs;
import dev.awaken.gates.run.Satchel;
import dev.awaken.gates.world.Cells;
import dev.awaken.gates.world.Hash;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Public API of the gates module (SPEC section 12). Stable signatures; everything else in the package may change.
 */
public final class Gates {
    private Gates() {}

    /** What anyone can know about a gate from outside. */
    public record GateInfo(int id, BlockPos pos, Rank rank, GateState state, long breaksInDays, boolean scripted) {}

    /** A player's current run. */
    public record RunInfo(int gateId, Theme theme, Rank rank, int area, int floor, int floors, int satchelSlots,
                          double satchelXp, int kills, long ticksOnFloor) {}

    /** Everything about a gate (what Veil scouting reveals). Frozen at creation: what you see is what's inside. */
    public record GatePreview(int id, Rank rank, Theme theme, String omen, Layout layout, List<Modifier> modifiers, int floors,
                              String bossId, String bossName, Twist twist, long breaksInDays, List<String> combos) {}

    private static MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    private static GateInfo info(MinecraftServer s, GateRecord g) {
        return new GateInfo(g.id, g.pos, g.plan.rank(), g.state, g.breaksIn(PlayDays.current(s)), g.scripted);
    }

    /** The gate whose surface structure stands at (or within 4 blocks of) pos, or whose cell contains pos in the gate dimension. */
    public static Optional<GateInfo> gateAt(ServerLevel level, BlockPos pos) {
        MinecraftServer s = level.getServer();
        GateRegistry reg = GateRegistry.get(s);
        if (GateService.inGate(level, pos)) {
            GateRecord g = reg.byCell(Cells.cellAt(pos.getX(), pos.getZ()));
            return g == null ? Optional.empty() : Optional.of(info(s, g));
        }
        if (level.dimension() != Level.OVERWORLD) return Optional.empty();
        for (GateRecord g : reg.near(pos, 6, false)) if (g.state != GateState.RELEASED) return Optional.of(info(s, g));
        return Optional.empty();
    }

    public static Optional<RunInfo> activeRun(ServerPlayer p) {
        RunState r = Runs.run(p);
        GateRecord g = Runs.gateOf(p);
        if (g == null) return Optional.empty();
        long t = GateService.now(p.level().getServer()) - r.floorStartedAt;
        return Optional.of(new RunInfo(g.id, g.plan.theme(), g.plan.rank(), r.area, r.floor, r.area == 0 ? g.plan.floors() : 2,
                Satchel.count(r.satchel), r.satchelXp, r.kills, t));
    }

    /** In the gate dimension (inside a live cell). Works for any entity. */
    public static boolean isInGate(Entity e) {
        if (!GateService.isGateLevel(e.level())) return false;
        if (!(e.level() instanceof ServerLevel sl)) return true;
        return GateRegistry.get(sl.getServer()).byCell(Cells.cellAt(e.getBlockX(), e.getBlockZ())) != null;
    }

    /**
     * Puts loot into the player's Satchel (loot from other modules found inside a gate). The stack is shrunk by what
     * moved; returns the same stack holding what didn't fit (empty when it all fit, unchanged when not in a run).
     */
    public static ItemStack satchelAdd(ServerPlayer p, ItemStack stack) {
        return Runs.satchelAdd(p, stack);
    }

    public static Optional<GatePreview> preview(int gateId) {
        MinecraftServer s = server();
        return s == null ? Optional.empty() : preview(s, gateId);
    }

    public static Optional<GatePreview> preview(MinecraftServer s, int gateId) {
        GateRecord g = GateRegistry.get(s).gate(gateId);
        if (g == null || g.state == GateState.RELEASED) return Optional.empty();
        GatePlan p = g.plan;
        return Optional.of(new GatePreview(g.id, p.rank(), p.theme(), p.theme().omen, p.layout(), p.modifiers(), p.floors(), p.boss(),
                p.theme().bossName, p.twist(), g.breaksIn(PlayDays.current(s)), p.combos()));
    }

    /** The Veil scouted this gate: from now on its card shows everything. Counts {@code awaken:gates_scouted} once. */
    public static void markScouted(ServerPlayer p, int gateId) {
        GateMemory mem = p.getData(GatesRegistry.MEMORY.get());
        if (mem.scouted.contains(gateId)) return;
        if (GateRegistry.get(p.level().getServer()).gate(gateId) == null) return;
        mem.scout(gateId);
        Progression.count(p, "awaken:gates_scouted");
        DirectorLog.log(p, "gate", "scouted gate " + gateId + " from the Veil");
        GateCard.reset(p);
    }

    public static boolean isScouted(ServerPlayer p, int gateId) {
        return p.getData(GatesRegistry.MEMORY.get()).scouted.contains(gateId);
    }

    /** The story prologue's gate: a forced E-rank Bell Crypt with no twist and no modifiers. */
    public static GatePlan prologuePlan(long seed) {
        return new GatePlan(Hash.mix(seed), GatePlan.CONTENT_VERSION, Rank.E, Theme.BELL_CRYPT, Layout.LABYRINTH, List.of(), Twist.NONE,
                Curve.floorsFor(Rank.E, false), Theme.BELL_CRYPT.bossId(), Theme.OVERGROWN_TEMPLE, 0);
    }

    /**
     * Opens a scripted gate 16–24 blocks in front of the player (story prologue). It never breaks and doesn't count
     * toward the Director's 1–4. Returns the gate id (-1 if no gate dimension).
     */
    public static int openScripted(ServerPlayer p, GatePlan plan) {
        MinecraftServer s = p.level().getServer();
        if (GateService.gateLevel(s) == null) return -1;
        ServerLevel over = s.overworld();
        var look = p.getLookAngle();
        double len = Math.max(0.01, Math.hypot(look.x, look.z));
        int x = p.getBlockX() + (int) Math.round(look.x / len * 20), z = p.getBlockZ() + (int) Math.round(look.z / len * 20);
        BlockPos ground = over.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
        GateRegistry reg = GateRegistry.get(s);
        int id = reg.nextId();
        List<String> trace = new ArrayList<>();
        trace.add("scripted  " + plan.rank().label + " " + plan.theme().displayName + " for " + p.getGameProfile().name());
        int axis = Math.abs(look.x) > Math.abs(look.z) ? 1 : 0;
        GateRecord g = GateService.create(s, id, plan, ground, axis, Optional.of(p.getUUID()), 9999, trace, true);
        return g.id;
    }

    public static List<GateInfo> near(ServerLevel level, BlockPos pos, int radius) {
        MinecraftServer s = level.getServer();
        List<GateInfo> out = new ArrayList<>();
        if (level.dimension() != Level.OVERWORLD) return out;
        for (GateRecord g : GateRegistry.get(s).near(pos, radius, false)) out.add(info(s, g));
        return out;
    }

    /** Story: end the player's run now; {@code keepSatchel} banks it (the prologue's "this one time, your loot is kept"). */
    public static boolean forceExtract(ServerPlayer p, boolean keepSatchel) {
        GateRecord g = Runs.gateOf(p);
        if (g == null) return false;
        RunState run = Runs.run(p);
        if (!keepSatchel) {
            run.satchel.clear();
            run.satchelXp = 0;
        }
        Runs.extract(p, true);
        return true;
    }

    /** Sends the beacon list to a player (other modules may call this after teleporting someone). */
    public static void refresh(ServerPlayer p) {
        GatePayloads.syncBeacons(p);
    }
}
