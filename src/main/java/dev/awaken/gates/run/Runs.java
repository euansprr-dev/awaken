package dev.awaken.gates.run;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.gates.Claim;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GateState;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.gates.world.Cells;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A player's run through a gate: entering, floors, the Satchel, clearing floors, extracting, the Lord, death and
 * coming back. Server thread only.
 */
public final class Runs {
    private Runs() {}

    public static final int FRACTURE_TICKS = 8 * 60 * 20;

    public static RunState run(ServerPlayer p) {
        return p.getData(GatesRegistry.RUN.get());
    }

    public static GateRecord gateOf(ServerPlayer p) {
        RunState r = run(p);
        return r.active() ? GateRegistry.get(p.level().getServer()).gate(r.gateId) : null;
    }

    static long now(MinecraftServer s) {
        return GateService.now(s);
    }

    /** XP one floor of this gate pays this player (SPEC section 2), below-rank gates ×0.3. */
    public static double floorReward(ServerPlayer p, GateRecord g, int area, int floor) {
        int depth = area == 0 ? floor : (g.plan.is(Twist.GATE_WITHIN) ? g.plan.innerGateFloor() + floor : g.plan.floors() + floor);
        double r = Curve.floorReward(g.plan.rank(), depth);
        if (g.plan.rank().ordinal() < Progression.rank(p).ordinal()) r *= Curve.BELOW_RANK_FACTOR;
        return r;
    }

    static String claimId(GateRecord g, RunState run, String floorLabel, ServerPlayer p) {
        return Claim.id(g.id, floorLabel + (run.runIndex > 0 ? "#" + run.runIndex : ""), p.getUUID());
    }

    static String label(int area, int floor) {
        return area == 0 ? Integer.toString(floor) : "a" + floor;
    }

    // ---- entering ---------------------------------------------------------------------------------------------------

    public static boolean enter(ServerPlayer p, GateRecord g) {
        MinecraftServer server = p.level().getServer();
        ServerLevel gl = GateService.gateLevel(server);
        if (gl == null || !AwakenConfig.GATES.get()) return false;
        RunState cur = run(p);
        if (cur.active()) {
            if (cur.gateId != g.id) Signal.toast(p, Component.literal("You're still inside another gate."));
            return false;
        }
        if (!g.state.enterable() || !g.interiorReady) {
            Signal.toast(p, Component.literal(g.state == GateState.BUILDING ? "The gate is still forming." : "The gate won't open."));
            return false;
        }
        RunState run = new RunState();
        run.gateId = g.id;
        run.area = 0;
        run.floor = 1;
        run.startedAt = now(server);
        run.runIndex = g.entries.merge(p.getUUID(), 1, Integer::sum) - 1;
        run.scoutedAtEntry = p.getData(GatesRegistry.MEMORY.get()).scouted.contains(g.id);
        p.setData(GatesRegistry.RUN.get(), run);
        g.party.add(p.getUUID());
        g.inside.add(p.getUUID());
        GateService.setState(server, g, GateState.ACTIVE);
        GateRegistry.get(server).setDirty();
        BlockPos e = GateService.entrance(g, 0, 1);
        p.teleport(new TeleportTransition(gl, GateService.center(e), Vec3.ZERO, GateService.entranceYaw(g, 0, 1), 0F,
                TeleportTransition.PLAY_PORTAL_SOUND));
        gl.playSound(null, e, GatesRegistry.SND_ENTER.get(), SoundSource.PLAYERS, 1F, 1F);
        GatePlan plan = g.plan;
        var w = SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal(plan.rank().label + "-rank · " + plan.theme().displayName))
                .row("Floor", "1 of " + plan.floors())
                .row("Layout", plan.layout().displayName + " · " + plan.layout().hint);
        if (!plan.modifiers().isEmpty()) w.row("Modifiers", String.join(", ", plan.modifiers().stream().map(m -> m.displayName).toList()));
        String twistLine = Twists.entryLine(plan);
        if (twistLine != null) w.body(Component.literal(twistLine).withStyle(plan.is(Twist.RED_GATE) ? ChatFormatting.RED : ChatFormatting.WHITE));
        w.footer(Component.literal(plan.friendly() ? "No one here wants to fight you." : "Find the Rest Shrine."));
        Signal.send(p, w.priority(4).ttl(20 * 8).build());
        DirectorLog.log(p, "gate", "entered gate " + g.id + " (run " + run.runIndex + ")");
        arrive(p, g, 0, 1);
        return true;
    }

    /** Puts the player on a floor (already teleported there): resets the floor clock and wakes the floor. */
    static void arrive(ServerPlayer p, GateRecord g, int area, int floor) {
        MinecraftServer server = p.level().getServer();
        RunState run = run(p);
        run.area = area;
        run.floor = floor;
        run.floorStartedAt = now(server);
        run.damageFloor = 0;
        run.killXpFloor = 0;
        run.noise = 0;
        FloorLayout f = GateLayout.of(g.plan).floor(area, floor);
        FloorState fs = g.floor(area, floor);
        ServerLevel gl = GateService.gateLevel(server);
        if (fs.enteredAt < 0) {
            fs.enteredAt = now(server);
            if (gl != null && f != null) Rooms.floorFirstEntered(gl, g, f);
        }
        Modifiers.applyTo(p, g);
        Twists.arrive(p, g, run, f);
        if (area == 0 && floor < g.plan.floors()) GateService.pregenFloor(server, g, 0, floor + 1);
        if (area == 1 && floor == 1) GateService.pregenFloor(server, g, 1, 2);
        if (floor > 1 || area == 1) {
            Signal.toast(p, Component.literal((area == 1 ? (g.plan.is(Twist.GATE_WITHIN) ? "Inner gate · " : "Second gate · ") : "")
                    + "Floor " + floor + (f != null && f.bossFloor ? " · the Lord waits" : "")));
        }
        GateRegistry.get(server).setDirty();
        Hud.send(p);
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            RunState run = run(p);
            if (!run.active()) continue;
            if ((t + p.getId()) % 10 != 0) continue;
            tickPlayer(server, p, run, t);
        }
    }

    static void tickPlayer(MinecraftServer server, ServerPlayer p, RunState run, int t) {
        GateRecord g = GateRegistry.get(server).gate(run.gateId);
        if (g == null || g.state == GateState.RELEASED) {
            lost(p, run, "The gate collapsed behind you. Your Satchel came with you.");
            return;
        }
        if (!GateService.inGate(p)) {
            if (p.isAlive()) lost(p, run, "You were pulled out of the gate. Your Satchel came with you.");
            return;
        }
        ServerLevel gl = (ServerLevel) p.level();
        Where w = Where.at(server, p.blockPosition());
        if (w == null || w.gate().id != g.id || w.floor() == null) {
            // fell out of the dungeon (or noclip): put them back at the floor's entrance
            if (p.getY() < Cells.floorY(6) - 40 || w == null || w.gate().id != g.id) {
                BlockPos e = GateService.entrance(g, run.area, run.floor);
                p.teleportTo(gl, e.getX() + 0.5, e.getY(), e.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
            }
            return;
        }
        if (w.area() != run.area || w.floorNo() != run.floor) arrive(p, g, w.area(), w.floorNo());
        FloorLayout f = w.floor();
        Room r = w.room();
        if (r != null) {
            Rooms.enter(gl, p, g, f, r, w.x(), w.z());
            if (r.id == f.end && !f.bossFloor) floorCleared(p, g, run, f);
        }
        Modifiers.tickPlayer(p, g, run, f, r);
        Twists.tickPlayer(p, g, run, f, r);
        if (((t + p.getId()) / 10) % 2 == 0) Hud.send(p);
    }

    /** Per gate, once a second: pressure, rising water, collapse, twist actors, room bookkeeping. */
    static void tickGates(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        ServerLevel gl = GateService.gateLevel(server);
        if (gl == null) return;
        for (GateRecord g : List.copyOf(GateRegistry.get(server).all())) {
            if (g.state != GateState.ACTIVE && !(g.state == GateState.CLEARED && !g.inside.isEmpty())) continue;
            Pressure.tick(gl, g);
            Modifiers.tickGate(gl, g);
            Twists.tickGate(gl, g);
        }
    }

    // ---- floors -----------------------------------------------------------------------------------------------------

    static void floorCleared(ServerPlayer p, GateRecord g, RunState run, FloorLayout f) {
        if (run.clearedFloor(f.area, f.floor)) return;
        MinecraftServer server = p.level().getServer();
        run.cleared.add(f.area + ":" + f.floor);
        run.floorsCleared++;
        double reward = floorReward(p, g, f.area, f.floor);
        double bonus = reward * (1 - Curve.KILL_SHARE);
        run.satchelXp += bonus;
        if (f.plannedMobs == 0) {
            // no monsters to pay the kill share (Friendly, Hollow): it lands now
            Progression.grantXp(p, reward * Curve.KILL_SHARE, Progression.XpSource.GATE_KILL);
        }
        Progression.count(p, Counters.FLOORS_CLEARED);
        Progression.count(p, Counters.FLOOR_STREAK_NO_EARLY_EXTRACT);
        GateMemory mem = p.getData(GatesRegistry.MEMORY.get());
        if (f.area == 0 && f.floor > mem.comfortFloor()) Progression.count(p, "awaken:floors_pushed_past_comfort");
        long took = now(server) - run.floorStartedAt;
        mem.echoFloorTicks = took;
        mem.echoFloorRooms = Math.max(1, f.criticalPath().size());
        Twists.floorCleared(p, g, run, f, took);
        float heal = p.getMaxHealth() * 0.3F;
        Modifiers.allowHeal(p);
        p.heal(heal);
        p.level().playSound(null, p.blockPosition(), GatesRegistry.SND_SHRINE.get(), SoundSource.PLAYERS, 1F, 1F);
        boolean red = g.plan.is(Twist.RED_GATE) && !g.bossDead;
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Floor " + f.floor + " cleared"))
                .row("Satchel XP", "+" + Math.round(bonus) + "  (carrying " + Math.round(run.satchelXp) + ")")
                .row("Satchel", Satchel.count(run.satchel) + " / " + Satchel.SLOTS + " slots")
                .body(Component.literal(red ? "The exit is sealed. Only the Seal remains."
                        : "Rest at the Shrine to carry it out, or break the Seal and go deeper."))
                .ttl(20 * 7).build());
        DirectorLog.log(p, "gate", "floor " + label(f.area, f.floor) + " of gate " + g.id + " cleared in " + took / 20 + " s");
        Hud.send(p);
    }

    /** Through the Seal: one floor deeper (main → next floor; second/inner gate floor 1 → 2). */
    public static void descend(ServerPlayer p) {
        RunState run = run(p);
        GateRecord g = gateOf(p);
        if (g == null) return;
        if (!run.clearedFloor(run.area, run.floor)) {
            Signal.toast(p, Component.literal("The Seal won't break until the floor is yours."));
            return;
        }
        int next = run.floor + 1;
        FloorLayout f = GateLayout.of(g.plan).floor(run.area, next);
        if (f == null) return;
        ServerLevel gl = (ServerLevel) p.level();
        BlockPos e = GateService.entrance(g, run.area, next);
        gl.playSound(null, p.blockPosition(), GatesRegistry.SND_SEAL.get(), SoundSource.PLAYERS, 1F, 1F);
        p.teleportTo(gl, e.getX() + 0.5, e.getY(), e.getZ() + 0.5, Set.of(), GateService.entranceYaw(g, run.area, next), 0F, true);
        if (run.area == 0 && next > p.getData(GatesRegistry.MEMORY.get()).comfortFloor()) {
            DirectorLog.log(p, "gate", "deeper than your usual (" + p.getData(GatesRegistry.MEMORY.get()).comfortFloor() + ")");
        }
        arrive(p, g, run.area, next);
    }

    // ---- leaving ----------------------------------------------------------------------------------------------------

    /** Rest Shrine → Extract: claim the Satchel and walk out of the gate mouth. */
    public static void extract(ServerPlayer p, boolean force) {
        RunState run = run(p);
        GateRecord g = gateOf(p);
        if (g == null) return;
        if (!force && g.plan.is(Twist.RED_GATE) && !g.bossDead) {
            Signal.toast(p, Component.literal("The exit is sealed. Only the Lord's death opens it."));
            return;
        }
        if (!force && !run.clearedFloor(run.area, run.floor)) return;
        bankSatchel(p, g, run, label(run.area, run.floor), "extract", "");
        GateMemory mem = p.getData(GatesRegistry.MEMORY.get());
        if (run.area == 0) mem.extractedAt(run.floor);
        boolean early = !(run.area == 0 && run.floor >= g.plan.floors());
        if (early) Progression.setCounter(p, Counters.FLOOR_STREAK_NO_EARLY_EXTRACT, 0);
        finishedGate(p, g, run);
        endRun(p, g, true);
        Claims.deliver(p);
    }

    /** Moves the whole Satchel into a PENDING claim (loot moves; nothing is copied). */
    static boolean bankSatchel(ServerPlayer p, GateRecord g, RunState run, String floorLabel, String kind, String note) {
        if (run.satchel.isEmpty() && run.satchelXp <= 0) return true;
        List<ItemStack> items = Satchel.drain(run.satchel);
        double xp = run.satchelXp;
        run.satchelXp = 0;
        Claim c = new Claim(claimId(g, run, floorLabel, p), p.getUUID(), g.id, items, xp, kind, note);
        if (!Claims.write(p, c)) {
            // already claimed under this id: put it back where it was rather than lose it
            for (ItemStack s : items) Satchel.add(run.satchel, s);
            run.satchelXp = xp;
            return false;
        }
        return true;
    }

    /** Things that happen once a run ends well (extracted or the Lord fell): Day's End, twists seen. */
    static void finishedGate(ServerPlayer p, GateRecord g, RunState run) {
        Claims.countGateToday(p);
        GateMemory mem = p.getData(GatesRegistry.MEMORY.get());
        if (g.plan.twist() != Twist.NONE && mem.countTwist(g.id)) Progression.count(p, Counters.TWISTS_SEEN);
    }

    /** Walking into the exit portal after the Lord: bank whatever was picked up since, and leave. */
    public static void exitPortal(ServerPlayer p) {
        RunState run = run(p);
        GateRecord g = gateOf(p);
        if (g == null) return;
        if (!(g.bossDead || g.annexBossDead)) return;
        bankSatchel(p, g, run, "exit", "extract", "");
        endRun(p, g, true);
        Claims.deliver(p);
    }

    /** Ends the run; {@code teleport} sends the player out of the gate mouth. */
    public static void endRun(ServerPlayer p, GateRecord g, boolean teleport) {
        MinecraftServer server = p.level().getServer();
        Modifiers.removeFrom(p);
        Twists.leave(p, g);
        p.setData(GatesRegistry.RUN.get(), RunState.none());
        if (g != null) {
            g.inside.remove(p.getUUID());
            if (g.inside.isEmpty()) {
                if (g.state == GateState.ACTIVE) GateService.setState(server, g, GateState.OPEN);
                else if (g.state == GateState.CLEARED) GateService.release(server, g);
            }
            GateRegistry.get(server).setDirty();
        }
        if (teleport && p.isAlive()) {
            ServerLevel over = server.overworld();
            BlockPos to = g != null && g.structurePlaced ? g.mouth() : over.getRespawnData().pos();
            if (g == null || !g.structurePlaced) to = over.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, to);
            float yaw = g == null ? 0 : (g.axis == 0 ? 0F : -90F);
            p.teleport(new TeleportTransition(over, GateService.center(to), Vec3.ZERO, yaw, 0F, TeleportTransition.PLAY_PORTAL_SOUND));
            over.playSound(null, to, GatesRegistry.SND_EXIT.get(), SoundSource.PLAYERS, 1F, 1F);
        }
        Hud.clear(p);
        if (g != null) GatePayloads.syncBeaconsAround(server, g.pos);
    }

    /** The run's gate is gone (or you were pulled out): keep the Satchel, send you home. */
    static void lost(ServerPlayer p, RunState run, String why) {
        MinecraftServer server = p.level().getServer();
        GateRecord g = GateRegistry.get(server).gate(run.gateId);
        if (!run.satchel.isEmpty() || run.satchelXp > 0) {
            List<ItemStack> items = Satchel.drain(run.satchel);
            Claim c = new Claim(run.gateId + ":lost" + run.runIndex + "-" + run.startedAt + ":" + p.getUUID(), p.getUUID(), run.gateId,
                    items, run.satchelXp, "recover", why);
            if (!Claims.write(p, c)) for (ItemStack s : items) Satchel.add(run.satchel, s);
            run.satchelXp = 0;
        }
        DirectorLog.log(p, "gate", "run in gate " + run.gateId + " ended: " + why);
        boolean inGate = GateService.inGate(p);
        if (g != null) endRun(p, g, inGate);
        else {
            Modifiers.removeFrom(p);
            p.setData(GatesRegistry.RUN.get(), RunState.none());
            Hud.clear(p);
            if (inGate) {
                ServerLevel over = server.overworld();
                BlockPos to = over.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, over.getRespawnData().pos());
                p.teleport(new TeleportTransition(over, GateService.center(to), Vec3.ZERO, 0F, 0F, TeleportTransition.PLAY_PORTAL_SOUND));
            }
        }
        Claims.deliver(p);
    }

    // ---- the Lord ---------------------------------------------------------------------------------------------------

    /** The Gate Lord (or the second gate's Lord, or the inner gate's keeper) fell. Everyone on that floor claims. */
    public static void lordDefeated(ServerLevel level, GateRecord g, FloorLayout f, LivingEntity lord, ServerPlayer killer) {
        MinecraftServer server = level.getServer();
        boolean main = f.area == 0;
        if (main) {
            if (g.bossDead) return;
            g.bossDead = true;
            g.clearedAt = now(server);
            GateService.setState(server, g, GateState.CLEARED);
        } else {
            if (g.annexBossDead) return;
            g.annexBossDead = true;
        }
        Room arena = f.room(f.end);
        Rooms.lock(level, g, f, arena, false);
        long t = now(server);
        for (ServerPlayer p : level.players()) {
            RunState run = run(p);
            if (run.gateId != g.id || run.area != f.area || run.floor != f.floor) continue;
            claimLord(p, g, run, f, killer == p, t);
        }
        Twists.lordDefeated(level, g, f, arena);
        GateRegistry.get(server).setDirty();
    }

    private static void claimLord(ServerPlayer p, GateRecord g, RunState run, FloorLayout f, boolean killer, long t) {
        boolean main = f.area == 0;
        GatePlan plan = g.plan;
        run.cleared.add(f.area + ":" + f.floor);
        double reward = floorReward(p, g, f.area, f.floor);
        run.satchelXp += reward * (1 - Curve.KILL_SHARE);
        GateMemory mem = p.getData(GatesRegistry.MEMORY.get());
        boolean pity = mem.bossesSinceCore >= 3;
        List<ItemStack> hoard = Loot.boss(plan.rank(), f.theme, p.getRandom(), plan.has(Modifier.FORTUNE), pity,
                Progression.perk(p, Perks.LOOT_BONUS));
        boolean core = hoard.stream().anyMatch(Loot::isPowerCore);
        mem.bossesSinceCore = core ? 0 : mem.bossesSinceCore + 1;
        for (ItemStack s : hoard) {
            if (Satchel.add(run.satchel, s) == 0 || !s.isEmpty()) run.satchel.add(s); // the hoard never gets lost to a full Satchel
        }
        bankSatchel(p, g, run, label(f.area, f.floor), "boss", "");
        Progression.count(p, Counters.FLOORS_CLEARED);
        if (main) {
            Progression.count(p, Counters.GATES_CLEARED);
            if (run.damageFloor <= 0) Progression.count(p, Counters.BOSS_FLOORS_NO_DAMAGE);
            if (run.damageRun <= 0) Progression.count(p, Counters.GATES_NO_HIT);
            if (killer && p.getMainHandItem().isEmpty()) Progression.count(p, "awaken:bosses_killed_unarmed");
            if (plan.is(Twist.RED_GATE)) Progression.setCounter(p, "awaken:latent/red_gate_cleared", 1);
            if (!run.scoutedAtEntry && t - run.startedAt <= 20 * 60 * 20) Progression.setCounter(p, "awaken:gate_cleared_unscouted_fast", 1);
            if (g.party.size() == 1 && !Twists.echoesSeen(g)) Progression.count(p, "awaken:gates_solo_no_echo");
            mem.twistStreak = plan.twist() != Twist.NONE ? mem.twistStreak + 1 : 0;
            if (mem.twistStreak >= 3) Progression.setCounter(p, "awaken:latent/three_twists_row", 1);
            if (mem.themesCleared.add(plan.theme().id)) Progression.markNewThing(p, "gate theme " + plan.theme().displayName);
            String moment = plan.theme().displayName + " " + plan.rank().label + "-rank cleared"
                    + (run.damageRun <= 0 ? ", no hits" : run.damageFloor <= 0 ? ", Lord untouched" : "")
                    + " in " + (t - run.startedAt) / 1200 + " min";
            if (run.damageRun <= 0 || plan.twist() != Twist.NONE || plan.rank().ordinal() > Progression.rank(p).ordinal()) Progression.bestMoment(p, moment);
            finishedGate(p, g, run);
        } else if (plan.is(Twist.DOUBLE_GATE)) {
            Progression.setCounter(p, "awaken:latent/double_gate_cleared", 1);
            Progression.bestMoment(p, "Survived a Double Gate (" + plan.theme().displayName + ")");
        }
        p.level().playSound(null, p.blockPosition(), GatesRegistry.SND_CLEARED.get(), SoundSource.PLAYERS, 1.2F, 1F);
        String title = main ? "Gate cleared" : plan.is(Twist.DOUBLE_GATE) ? "The second gate falls" : "The inner gate is quiet";
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal(title))
                .row("Lord", main ? Rooms.capital(plan.theme().bossName) : "the keeper")
                .row("Claimed", "the Satchel and the hoard")
                .body(Component.literal(main && plan.is(Twist.DOUBLE_GATE) ? "A wall cracks open behind the Lord's throne..."
                        : main ? "The way out is open." : "A way back opens."))
                .priority(6).ttl(20 * 9).build());
        Hud.send(p);
    }

    // ---- death ------------------------------------------------------------------------------------------------------

    /** HIGH priority: runs before other death listeners so Cheat Death can cancel the death. */
    static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        RunState run = run(p);
        if (!run.active()) return;
        MinecraftServer server = p.level().getServer();
        GateRecord g = GateRegistry.get(server).gate(run.gateId);
        if (Progression.perk(p, Perks.CHEAT_DEATH_PER_GATE) > 0 && !run.cheatDeathUsed) {
            e.setCanceled(true);
            run.cheatDeathUsed = true;
            Modifiers.allowHeal(p);
            p.setHealth(Math.max(1F, p.getMaxHealth() * 0.4F));
            p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 3));
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
            Signal.toast(p, Component.literal("Nothing can touch you. Not yet."));
            DirectorLog.log(p, "gate", "cheat death used in gate " + run.gateId);
            return;
        }
        AwakenConfig.Difficulty diff = AwakenConfig.DIFFICULTY.get();
        List<ItemStack> kept = satchelOnDeath(run, diff);
        if (g != null && (!kept.isEmpty() || (diff == AwakenConfig.Difficulty.STORY && run.satchelXp > 0))) {
            double xp = diff == AwakenConfig.Difficulty.STORY ? run.satchelXp * 0.5 : 0;
            Claims.write(p, new Claim(claimId(g, run, "d" + label(run.area, run.floor), p), p.getUUID(), g.id, kept, xp, "death",
                    "Story mode: half of what you carried is yours."));
        }
        run.satchelXp = 0;
        if (diff != AwakenConfig.Difficulty.STORY) Progression.fracture(p, FRACTURE_TICKS);
        if (g != null) {
            FloorLayout f = GateLayout.of(g.plan).floor(run.area, run.floor);
            if (f != null && f.bossFloor) Progression.addInsight(p, g.plan.boss());
        }
        stashGear(p);
        DirectorLog.log(p, "gate", "died in gate " + run.gateId + " on floor " + label(run.area, run.floor) + " (" + diff + ")");
        endRun(p, g, false);
    }

    /** What the Satchel leaves you on death: STORY keeps half, every other difficulty keeps nothing. Empties the Satchel. */
    public static List<ItemStack> satchelOnDeath(RunState run, AwakenConfig.Difficulty diff) {
        if (diff == AwakenConfig.Difficulty.STORY) return Satchel.keepHalf(run.satchel);
        run.satchel.clear();
        return List.of();
    }

    /** Your gear survives a gate death: it is held aside before the inventory would drop, and returned on respawn. */
    static void stashGear(ServerPlayer p) {
        Inventory inv = p.getInventory();
        List<GearStash.Slot> slots = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) slots.add(new GearStash.Slot(i, s.copy()));
        }
        inv.clearContent();
        p.setData(GatesRegistry.STASH.get(), new GearStash(slots, p.experienceLevel, p.experienceProgress, p.totalExperience));
        p.experienceLevel = 0;
        p.experienceProgress = 0;
        p.totalExperience = 0;
    }

    static void restoreGear(ServerPlayer p) {
        GearStash s = p.getData(GatesRegistry.STASH.get());
        if (s.isEmpty()) return;
        Inventory inv = p.getInventory();
        for (GearStash.Slot slot : s.slots()) {
            ItemStack st = slot.stack().copy();
            if (slot.index() < inv.getContainerSize() && inv.getItem(slot.index()).isEmpty()) inv.setItem(slot.index(), st);
            else if (!p.addItem(st)) p.drop(st, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        p.experienceLevel = s.xpLevel();
        p.experienceProgress = s.xpProgress();
        p.totalExperience = s.xpTotal();
        p.setData(GatesRegistry.STASH.get(), GearStash.EMPTY);
        Signal.toast(p, Component.literal("Your gear came back with you."));
    }

    static void onRespawn(ServerPlayer p) {
        restoreGear(p);
        Claims.deliver(p);
    }

    static void onLogin(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        RunState run = run(p);
        if (run.active()) {
            GateRecord g = GateRegistry.get(server).gate(run.gateId);
            if (g == null || g.state == GateState.RELEASED || g.state == GateState.BROKEN || !GateService.inGate(p)) {
                lost(p, run, "The gate collapsed behind you. Your Satchel came with you.");
            } else {
                g.inside.add(p.getUUID());
                GateRegistry.get(server).setDirty();
                Modifiers.applyTo(p, g);
                Hud.send(p);
            }
        } else if (GateService.inGate(p) && !p.isCreative() && !p.isSpectator()) {
            // standing in a gate with no run (a cell that was cleaned up): bring them home
            ServerLevel over = server.overworld();
            BlockPos to = over.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, over.getRespawnData().pos());
            p.teleport(new TeleportTransition(over, GateService.center(to), Vec3.ZERO, 0F, 0F, TeleportTransition.DO_NOTHING));
            Signal.toast(p, Component.literal("The gate you were in is gone."));
        }
        if (p.isAlive()) restoreGear(p);
        Claims.deliver(p);
    }

    // ---- kills and pickups ------------------------------------------------------------------------------------------

    /** Kill XP: 40% of the floor reward split across the floor's planned monsters, granted live, capped at that 40%. */
    static void onKill(ServerPlayer p, GateRecord g, LivingEntity dead, MobRole role) {
        RunState run = run(p);
        if (run.gateId != g.id) return;
        run.kills++;
        FloorLayout f = GateLayout.of(g.plan).floor(run.area, run.floor);
        if (f == null) return;
        double budget = floorReward(p, g, run.area, run.floor) * Curve.KILL_SHARE;
        int units = Math.max(1, xpUnits(f));
        int weight = switch (role.role()) {
            case ELITE -> 2;
            case WARDEN -> 4;
            default -> 1;
        };
        double give = Math.min(budget / units * weight, budget - run.killXpFloor);
        if (give > 0) {
            run.killXpFloor += give;
            Progression.grantXp(p, give, Progression.XpSource.GATE_KILL);
        }
        if (role.role() == MobRole.Role.ELITE || role.role() == MobRole.Role.WARDEN) {
            boolean bias = Progression.minutesSinceNewThing(p) > 20;
            for (ItemStack s : Loot.elite(g.plan.rank(), f.theme, p.getRandom(), g.plan.has(Modifier.FORTUNE), bias)) {
                String name = s.getHoverName().getString();
                boolean core = Loot.isPowerCore(s);
                int moved = Satchel.add(run.satchel, s);
                if (moved > 0 && core) Signal.toast(p, Component.literal("+ " + name + " (in your Satchel)"));
                if (!s.isEmpty()) {
                    // Satchel full: drop it where the elite fell; picking it up later still routes to the Satchel
                    ItemEntity drop = new ItemEntity(p.level(), dead.getX(), dead.getY() + 0.5, dead.getZ(), s);
                    p.level().addFreshEntity(drop);
                }
            }
        }
    }

    /** Monsters the kill share is split across: everything on the main path, half of what's off it. */
    static int xpUnits(FloorLayout f) {
        List<Integer> path = f.criticalPath();
        double u = 0;
        for (Room r : f.rooms) {
            double w = r.normals + r.wave2Normals + 2.0 * (r.elites + r.wave2Elites);
            u += path.contains(r.id) ? w : w * 0.5;
        }
        return (int) Math.ceil(u);
    }

    /** Everything you pick up inside a gate goes to the Satchel (except what you dropped yourself). */
    static void onPickup(ItemEntityPickupEvent.Pre e) {
        if (!(e.getPlayer() instanceof ServerPlayer p)) return;
        RunState run = run(p);
        if (!run.active() || !GateService.inGate(p)) return;
        ItemEntity item = e.getItemEntity();
        if (item.hasPickUpDelay() || (item.getTarget() != null && !item.getTarget().equals(p.getUUID()))) return;
        if (item.getOwner() == p) return;
        ItemStack stack = item.getItem();
        int moved = Satchel.add(run.satchel, stack);
        e.setCanPickup(TriState.FALSE);
        if (moved > 0) {
            p.take(item, moved);
            if (stack.isEmpty()) item.discard();
            Hud.sendSoon(p);
        } else if (p.tickCount % 40 == 0) {
            Signal.toast(p, Component.literal("Your Satchel is full."));
        }
    }

    /** Public API: put loot straight into the Satchel. Returns what didn't fit (empty if all of it did). */
    public static ItemStack satchelAdd(ServerPlayer p, ItemStack stack) {
        RunState run = run(p);
        if (!run.active()) return stack;
        Satchel.add(run.satchel, stack);
        Hud.sendSoon(p);
        return stack;
    }

    static Rank rankOf(GateRecord g) {
        return g.plan.rank();
    }

    static void placeBlock(ServerLevel level, BlockPos p, net.minecraft.world.level.block.state.BlockState s) {
        level.setBlock(p, s, Block.UPDATE_ALL);
    }

    static boolean inOverworld(ServerPlayer p) {
        return p.level().dimension() == Level.OVERWORLD;
    }

    static ItemStack shard(int n) {
        return new ItemStack(CoreApi.ESSENCE_SHARD.get(), n);
    }
}
