package dev.awaken.gates.run;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.entity.EchoGhost;
import dev.awaken.gates.entity.GateHoarder;
import dev.awaken.gates.entity.HollowFollower;
import dev.awaken.gates.world.Cells;
import dev.awaken.gates.world.FeatureBlocks;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Hash;
import dev.awaken.gates.world.Room;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The eight twists. Each is a small rule on top of an ordinary gate. */
public final class Twists {
    private Twists() {}

    // ---- text -------------------------------------------------------------------------------------------------------

    static String entryLine(GatePlan plan) {
        return switch (plan.twist()) {
            case RED_GATE -> "RED GATE. The exit has sealed behind you. Only the Lord's death opens it.";
            case HOLLOW_GATE -> "It's quiet. No monsters. Something follows you anyway.";
            case TREASURE_GATE -> "Something small is running off with this gate's treasure.";
            case INVERTED -> "You wake inside one of the Lord's minions. They think you're one of them.";
            case FRIENDLY -> "A friendly gate. A spirit here needs your help.";
            case ECHO_GATE -> "An echo of you runs this gate too. Beat it to each shrine.";
            case GATE_WITHIN -> "Something hums deeper in. A gate inside the gate.";
            case DOUBLE_GATE -> "This gate feels deeper than it should.";
            default -> plan.theme().friendly ? "Lanterns, music, someone haggling. Nobody here wants a fight." : null;
        };
    }

    static String ruleHint(int rule) {
        return switch (rule) {
            case 0 -> "Those who rush, miss.";
            case 1 -> "Nothing bleeds in the dark.";
            default -> "Never face what you strike.";
        };
    }

    static String hint(ServerPlayer p, GateRecord g, RunState run) {
        GatePlan plan = g.plan;
        if (run.area == 1 && plan.is(Twist.DOUBLE_GATE)) return "The second gate's law: " + ruleHint(plan.annexRule());
        if (plan.is(Twist.RED_GATE) && !g.bossDead) return "The exit is sealed";
        if (plan.friendly()) {
            FloorState fs = g.floor(run.area, run.floor);
            FloorLayout f = GateLayout.of(plan).floor(run.area, run.floor);
            int total = f == null ? 0 : lanternsOn(f);
            if (total > 0 && !fs.spiritDone) return "Lost lanterns " + fs.lanternsTaken.size() + " / " + total;
        }
        if (plan.is(Twist.INVERTED)) return disguised(p, g) ? "Disguised" : "Seen";
        if (plan.is(Twist.ECHO_GATE)) {
            EchoGhost ghost = ghostOf(p);
            if (ghost != null) return ghost.arrived() ? "Your echo is already there" : "Your echo is running";
        }
        if (plan.is(Twist.TREASURE_GATE)) {
            Entity h = HOARDERS.containsKey(floorKey(g, run.area, run.floor)) ? ((ServerLevel) p.level()).getEntity(HOARDERS.get(floorKey(g, run.area, run.floor))) : null;
            return h != null && h.isAlive() ? "The hoarder is somewhere on this floor" : null;
        }
        return null;
    }

    static int lanternsOn(FloorLayout f) {
        int n = 0;
        for (Room r : f.rooms) n += r.features(Room.FeatureKind.LANTERN).size();
        return n;
    }

    static String floorKey(GateRecord g, int area, int floor) {
        return g.id + ":" + area + ":" + floor;
    }

    // ---- state ------------------------------------------------------------------------------------------------------

    private static final Map<UUID, UUID> GHOSTS = new HashMap<>();
    private static final Map<UUID, UUID> PUPPETS = new HashMap<>();
    private static final Map<String, UUID> FOLLOWERS = new HashMap<>();
    private static final Map<String, UUID> HOARDERS = new HashMap<>();
    private static final Map<UUID, List<Long>> SAMPLES = new HashMap<>();
    private static final Set<Integer> ECHOES_SEEN = new HashSet<>();

    /** Server start/stop: nothing carries over to another world. */
    public static void resetAll() {
        GHOSTS.clear();
        PUPPETS.clear();
        FOLLOWERS.clear();
        HOARDERS.clear();
        SAMPLES.clear();
        ECHOES_SEEN.clear();
    }

    static EchoGhost ghostOf(ServerPlayer p) {
        UUID id = GHOSTS.get(p.getUUID());
        if (id == null) return null;
        Entity e = ((ServerLevel) p.level()).getEntity(id);
        return e instanceof EchoGhost g && g.isAlive() ? g : null;
    }

    static boolean echoesSeen(GateRecord g) {
        return ECHOES_SEEN.contains(g.id);
    }

    static boolean hasPuppet(ServerPlayer p) {
        return PUPPETS.containsKey(p.getUUID());
    }

    /** The Hollow follower or the Hoarder of a floor, if it is in the world. */
    static Entity actor(ServerLevel level, GateRecord g, int area, int floor, boolean follower) {
        UUID id = (follower ? FOLLOWERS : HOARDERS).get(floorKey(g, area, floor));
        return id == null ? null : level.getEntity(id);
    }

    // ---- floors -----------------------------------------------------------------------------------------------------

    static void floorFirstEntered(ServerLevel level, GateRecord g, FloorLayout f) {
        if (g.plan.is(Twist.TREASURE_GATE) && !f.bossFloor) {
            List<Room> rooms = new ArrayList<>();
            for (Room r : f.rooms) if (r.type.combat() && r.type != Room.Type.BOSS && !r.spawnSpots.isEmpty()) rooms.add(r);
            if (!rooms.isEmpty()) {
                Room r = rooms.get(Hash.range(g.plan.seed(), f.floor, f.area + 40, rooms.size()));
                List<BlockPos> spots = Rooms.spots(level, g, f, r);
                GateHoarder h = GatesRegistry.HOARDER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (h != null) {
                    BlockPos at = spots.get(0);
                    h.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                    h.gateId = g.id;
                    h.area = f.area;
                    h.floor = f.floor;
                    var spawnChance = h.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SPAWN_REINFORCEMENTS_CHANCE);
                    if (spawnChance != null) spawnChance.setBaseValue(0);
                    h.setCustomName(Component.literal("Hoarder"));
                    h.getPersistentData().putInt("awaken_gate_hoarder", g.id);
                    level.addFreshEntity(h);
                    HOARDERS.put(floorKey(g, f.area, f.floor), h.getUUID());
                }
            }
        }
    }

    static void arrive(ServerPlayer p, GateRecord g, RunState run, FloorLayout f) {
        ServerLevel level = (ServerLevel) p.level();
        discardGhost(p);
        SAMPLES.remove(p.getUUID());
        if (f == null) return;
        if (g.plan.is(Twist.ECHO_GATE) && !f.bossFloor && f.area == 0) spawnGhost(p, g, run, f);
        if (g.plan.is(Twist.INVERTED)) spawnPuppet(p, g, f);
        if (f.area == 1 && g.plan.is(Twist.DOUBLE_GATE) && f.floor == 1) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal("The second gate"))
                    .body(Component.literal("It keeps its own law. \"" + ruleHint(g.plan.annexRule()) + "\""))
                    .footer(Component.literal("Figure it out, or nothing here will bleed.")).priority(6).ttl(20 * 10).build());
        }
        if (f.area == 1 && g.plan.is(Twist.GATE_WITHIN) && f.floor == 1) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal("A gate within the gate"))
                    .row("Theme", f.theme.displayName).row("Floors", "2").body(Component.literal(f.theme.omen)).ttl(20 * 6).build());
        }
    }

    static void leave(ServerPlayer p, GateRecord g) {
        discardGhost(p);
        UUID puppet = PUPPETS.remove(p.getUUID());
        if (puppet != null && p.level() instanceof ServerLevel sl) {
            Entity e = sl.getEntity(puppet);
            if (e != null) e.discard();
        }
        p.removeEffect(MobEffects.INVISIBILITY);
        SAMPLES.remove(p.getUUID());
        Modifiers.forget(p.getUUID());
    }

    static void floorCleared(ServerPlayer p, GateRecord g, RunState run, FloorLayout f, long took) {
        ServerLevel level = (ServerLevel) p.level();
        if (g.plan.is(Twist.ECHO_GATE)) {
            EchoGhost ghost = ghostOf(p);
            if (ghost != null) {
                if (!ghost.arrived()) {
                    Signal.toast(p, Component.literal("You beat your echo here."));
                    for (ItemStack s : Loot.cache(g.plan.rank(), f.theme, p.getRandom(), false, 0)) Runs.satchelOrDrop(p, run, s);
                    level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
                } else Signal.toast(p, Component.literal("Your echo got here first."));
            }
            String key = run.area + ":" + run.floor + ":" + p.getUUID();
            List<Long> samples = SAMPLES.remove(p.getUUID());
            if (samples != null && samples.size() > 4 && !g.echoPaths.containsKey(key)) {
                g.echoPaths.put(key, new ArrayList<>(samples.subList(0, Math.min(samples.size(), 600))));
                GateRegistry.get(level.getServer()).setDirty();
            }
            discardGhost(p);
        }
        if (g.plan.layout() == dev.awaken.gates.content.Layout.STEALTH_HALLS && f.area == 0 && !g.floor(f.area, f.floor).alarm && !g.plan.friendly()) {
            Signal.toast(p, Component.literal("Unseen. The halls pay those who pass unheard."));
            for (ItemStack s : Loot.cache(g.plan.rank(), f.theme, p.getRandom(), false, 0)) Runs.satchelOrDrop(p, run, s);
        }
        String fk = floorKey(g, f.area, f.floor);
        UUID follower = FOLLOWERS.get(fk);
        if (follower != null) {
            boolean anyLeft = false;
            for (ServerPlayer o : level.players()) {
                RunState r = Runs.run(o);
                if (r.gateId == g.id && r.area == f.area && r.floor == f.floor && !r.clearedFloor(f.area, f.floor) && o != p) anyLeft = true;
            }
            if (!anyLeft) {
                Entity e = level.getEntity(follower);
                if (e != null) e.discard();
                FOLLOWERS.remove(fk);
            }
        }
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    static void tickPlayer(ServerPlayer p, GateRecord g, RunState run, FloorLayout f, Room r) {
        if (g.plan.is(Twist.ECHO_GATE)) {
            List<Long> s = SAMPLES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
            if (s.size() < 600) s.add(pack(p.position(), g.cell));
        }
        if (g.plan.is(Twist.INVERTED)) {
            boolean disguised = disguised(p, g);
            if (disguised) p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
            else p.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    /** Every tick: puppets follow their player closely (Inverted). */
    static void tickPuppets(MinecraftServer server) {
        if (PUPPETS.isEmpty()) return;
        PUPPETS.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            // a puppet stays in the level it was made in (the gates' or the open sky's) and goes when its player leaves it
            Entity puppet = null;
            for (ServerLevel gl : GateService.levels(server)) if (puppet == null) puppet = gl.getEntity(e.getValue());
            if (p == null || puppet == null || !puppet.isAlive() || p.level() != puppet.level()) {
                if (puppet != null) puppet.discard();
                return true;
            }
            Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
            Vec3 at = p.position().subtract(look.scale(0.55));
            puppet.setPos(at.x, p.getY(), at.z);
            puppet.setYRot(p.getYRot());
            puppet.setYHeadRot(p.getYHeadRot());
            if (puppet instanceof LivingEntity le) le.setYBodyRot(p.getYRot());
            puppet.setXRot(p.getXRot());
            return false;
        });
    }

    static void tickGate(ServerLevel level, GateRecord g) {
        long now = GateService.now(level.getServer());
        if (level.getServer().getTickCount() % 100 == 0 && !ECHOES_SEEN.contains(g.id)) {
            for (ServerPlayer p : level.players()) {
                if (Runs.run(p).gateId != g.id) continue;
                List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(24),
                        e -> e.getData(CoreApi.MOB_ROLE.get()).role() == MobRole.Role.ECHO);
                if (!near.isEmpty()) ECHOES_SEEN.add(g.id);
            }
        }
        if (g.plan.is(Twist.HOLLOW_GATE)) {
            Map<String, ServerPlayer> floors = new HashMap<>();
            for (ServerPlayer p : level.players()) {
                RunState r = Runs.run(p);
                if (r.gateId == g.id && p.isAlive() && !r.clearedFloor(r.area, r.floor)) floors.putIfAbsent(r.area + ":" + r.floor, p);
            }
            for (var e : floors.entrySet()) {
                RunState r = Runs.run(e.getValue());
                FloorLayout f = GateLayout.of(g.plan).floor(r.area, r.floor);
                if (f == null || f.bossFloor) continue;
                String fk = floorKey(g, r.area, r.floor);
                UUID id = FOLLOWERS.get(fk);
                Entity existing = id == null ? null : level.getEntity(id);
                if (existing != null && existing.isAlive()) continue;
                if (now - r.floorStartedAt < 15 * 20) continue;
                HollowFollower h = GatesRegistry.HOLLOW.get().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (h == null) continue;
                BlockPos at = GateService.entrance(g, r.area, r.floor);
                h.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                h.gateId = g.id;
                h.area = r.area;
                h.floor = r.floor;
                level.addFreshEntity(h);
                FOLLOWERS.put(fk, h.getUUID());
                Signal.toast(e.getValue(), Component.literal("Something came in after you."));
            }
        }
    }

    // ---- Echo Gate --------------------------------------------------------------------------------------------------

    static long pack(Vec3 p, int cell) {
        long x = (long) Math.floor((p.x - Cells.originX(cell)) * 4) & 0xFFFFF;
        long z = (long) Math.floor((p.z - Cells.originZ(cell)) * 4) & 0xFFFFF;
        long y = (long) Math.floor(p.y * 4) & 0xFFFFF;
        return (x << 40) | (y << 20) | z;
    }

    static Vec3 unpack(long v, int cell) {
        double x = ((v >>> 40) & 0xFFFFF) / 4.0 + Cells.originX(cell);
        double y = ((v >>> 20) & 0xFFFFF) / 4.0;
        double z = (v & 0xFFFFF) / 4.0 + Cells.originZ(cell);
        return new Vec3(x, y, z);
    }

    static void spawnGhost(ServerPlayer p, GateRecord g, RunState run, FloorLayout f) {
        ServerLevel level = (ServerLevel) p.level();
        List<Vec3> pts = new ArrayList<>();
        List<Integer> times = new ArrayList<>();
        List<Long> recorded = g.echoPaths.get(run.area + ":" + run.floor + ":" + p.getUUID());
        String how;
        if (recorded != null && recorded.size() > 4) {
            // replay the exact path you walked here before, at the speed you walked it
            for (int i = 0; i < recorded.size(); i++) {
                pts.add(unpack(recorded.get(i), g.cell));
                times.add(i * 10);
            }
            how = "your own path through this floor, at your own pace";
        } else {
            // walk this floor's main path at the pace of your last cleared floor
            GateMemory mem = p.getData(GatesRegistry.MEMORY.get());
            List<Integer> path = f.criticalPath();
            for (int i = 0; i < path.size(); i++) {
                Room r = f.room(path.get(i));
                pts.add(Vec3.atBottomCenterOf(GateService.roomFloor(g, f, r, r.centerX(), r.centerZ())));
                if (i + 1 < path.size()) {
                    Room.Door d = r.doorTo(path.get(i + 1));
                    if (d != null) pts.add(Vec3.atBottomCenterOf(GateService.world(g, f, d.centerX() - (d.side == 1 ? 1 : 0), d.y, d.centerZ() - (d.side == 2 ? 1 : 0))));
                }
            }
            long total = mem.echoFloorTicks > 0 ? mem.echoFloorTicks * path.size() / Math.max(1, mem.echoFloorRooms) : 3 * 60 * 20;
            total = Math.max(45 * 20, Math.min(10 * 60 * 20, total));
            double len = 0;
            for (int i = 1; i < pts.size(); i++) len += pts.get(i).distanceTo(pts.get(i - 1));
            double acc = 0;
            times.add(0);
            for (int i = 1; i < pts.size(); i++) {
                acc += pts.get(i).distanceTo(pts.get(i - 1));
                times.add((int) Math.round(total * acc / Math.max(1, len)));
            }
            how = mem.echoFloorTicks > 0 ? "the pace of your last floor" : "a steady pace";
        }
        if (pts.size() < 2) return;
        EchoGhost ghost = GatesRegistry.GHOST.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (ghost == null) return;
        Vec3 s = pts.get(0);
        ghost.snapTo(s.x, s.y, s.z, p.getYRot(), 0);
        ghost.route(p.getUUID(), pts, times);
        ghost.gateId = g.id;
        ghost.area = run.area;
        ghost.floor = run.floor;
        level.addFreshEntity(ghost);
        GHOSTS.put(p.getUUID(), ghost.getUUID());
        DirectorLog.log(p, "gate", "echo ghost on floor " + run.floor + ": " + how);
    }

    static void discardGhost(ServerPlayer p) {
        UUID id = GHOSTS.remove(p.getUUID());
        if (id != null && p.level() instanceof ServerLevel sl) {
            Entity e = sl.getEntity(id);
            if (e != null) e.discard();
        }
    }

    // ---- Inverted ---------------------------------------------------------------------------------------------------

    static void spawnPuppet(ServerPlayer p, GateRecord g, FloorLayout f) {
        ServerLevel level = (ServerLevel) p.level();
        UUID old = PUPPETS.remove(p.getUUID());
        if (old != null) {
            Entity e = level.getEntity(old);
            if (e != null) e.discard();
        }
        LivingEntity m = MobHooks.spawnThemed(level, f.theme.themeId(), MobRole.Role.NORMAL, p.blockPosition(),
                dev.awaken.progression.Progression.contentLevel(g.plan.rank()), java.util.Set.of(), "");
        if (m == null) return;
        if (m instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        m.setPermanentlyInvulnerable(true);
        m.noPhysics = true;
        m.setNoGravity(true);
        m.setSilent(true);
        m.getPersistentData().putBoolean("awaken_gate_puppet", true);
        m.setData(CoreApi.MOB_ROLE.get(), MobRole.NONE);
        PUPPETS.put(p.getUUID(), m.getUUID());
    }

    public static boolean disguised(ServerPlayer p, GateRecord g) {
        if (!g.plan.is(Twist.INVERTED)) return false;
        RunState run = Runs.run(p);
        if (run.gateId != g.id || GateService.now(p.level().getServer()) < run.disguiseBrokenUntil) return false;
        if (!PUPPETS.containsKey(p.getUUID())) return false;
        Where w = Where.at(p.level().getServer(), p.blockPosition());
        return w == null || w.room() == null || w.room().type != Room.Type.BOSS;
    }

    /** Inverted: the Lord's minions don't attack one of their own. */
    static void onTarget(LivingChangeTargetEvent e) {
        if (!(e.getNewAboutToBeSetTarget() instanceof ServerPlayer p)) return;
        if (!GateService.inGate(e.getEntity())) return;
        GateRecord g = Runs.gateOf(p);
        if (g == null) return;
        MobRole role = e.getEntity().getData(CoreApi.MOB_ROLE.get());
        if (!String.valueOf(g.id).equals(role.gateId())) return;
        if (g.plan.is(Twist.INVERTED) && role.role() != MobRole.Role.BOSS && disguised(p, g)) {
            e.setCanceled(true);
            return;
        }
        // Stealth Halls: being spotted raises the alarm once per floor
        if (g.plan.layout() == dev.awaken.gates.content.Layout.STEALTH_HALLS) {
            RunState run = Runs.run(p);
            if (run.area != 0) return;
            FloorState fs = g.floor(run.area, run.floor);
            if (fs.alarm) return;
            fs.alarm = true;
            ServerLevel level = (ServerLevel) p.level();
            FloorLayout f = GateLayout.of(g.plan).floor(run.area, run.floor);
            if (f == null) return;
            for (int i = 0; i < 3; i++) {
                BlockPos at = Pressure.spotNear(level, g, f, p, 8);
                if (at != null) Rooms.spawn(level, g, f, -1, MobRole.Role.NORMAL, at);
            }
            level.playSound(null, p.blockPosition(), GatesRegistry.SND_ALARM.get(), SoundSource.HOSTILE, 2F, 1F);
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Spotted")).body(Component.literal("The halls are awake.")).ttl(20 * 4).build());
            GateRegistry.get(level.getServer()).setDirty();
        }
    }

    // ---- hits: Double Gate law, Inverted, Treasure ------------------------------------------------------------------

    static void onPlayerHits(ServerPlayer attacker, GateRecord g, LivingEntity victim, LivingIncomingDamageEvent e) {
        ServerLevel level = (ServerLevel) attacker.level();
        if (g.plan.is(Twist.INVERTED)) {
            RunState run = Runs.run(attacker);
            if (disguised(attacker, g)) Signal.toast(attacker, Component.literal("They saw that."));
            run.disguiseBrokenUntil = GateService.now(level.getServer()) + 200;
        }
        if (g.plan.is(Twist.DOUBLE_GATE)) {
            GateMobs.Key key = GateMobs.keyOf(victim);
            if (key != null && key.area() == 1 && !lawHolds(g.plan.annexRule(), attacker, victim, level)) {
                e.setCanceled(true);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, victim.getX(), victim.getY() + 1, victim.getZ(), 6, 0.3, 0.4, 0.3, 0.1);
                level.playSound(null, victim.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 0.8F, 1.6F);
                return;
            }
        }
        // only a hit that lands (real damage, not inside its hurt frames), and only a handful per Hoarder
        if (victim instanceof GateHoarder h && e.getAmount() >= 1F && h.hurtTime == 0 && h.shardsShed < GateHoarder.MAX_SHED
                && attacker.getRandom().nextInt(2) == 0) {
            h.shardsShed++;
            ItemStack shard = new ItemStack(CoreApi.ESSENCE_SHARD.get(), 1);
            ItemStack left = Runs.satchelAdd(attacker, shard);
            if (!left.isEmpty()) level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, victim.getX(), victim.getY() + 0.5, victim.getZ(), left));
            level.playSound(null, victim.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 0.8F, 1.6F);
        }
    }

    /** The second gate's law: 0 strike while still, 1 strike in the light, 2 strike from behind. */
    public static boolean lawHolds(int rule, Player attacker, LivingEntity victim, ServerLevel level) {
        return switch (rule) {
            case 0 -> Math.hypot(attacker.getX() - attacker.xo, attacker.getZ() - attacker.zo) < 0.04;
            case 1 -> level.getBrightness(LightLayer.BLOCK, attacker.blockPosition()) >= 10;
            default -> {
                Vec3 facing = Vec3.directionFromRotation(0, victim.getYRot());
                Vec3 toAttacker = attacker.position().subtract(victim.position()).normalize();
                yield facing.dot(toAttacker) < -0.2;
            }
        };
    }

    // ---- the Lord ---------------------------------------------------------------------------------------------------

    static void bossArena(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f, Room r) {
        if (g.plan.friendly() && f.area == 0) {
            Rooms.lock(level, g, f, r, false);
            Signal.toast(p, Component.literal(Rooms.capital(g.plan.theme().bossName) + " is waiting for you. Talk to them."));
            return;
        }
        Room.Feature spot = r.feature(Room.FeatureKind.BOSS_SPOT);
        BlockPos at = GateService.roomFloor(g, f, r, spot == null ? r.centerX() : spot.x(), spot == null ? r.centerZ() : spot.z());
        LivingEntity lord = Rooms.spawn(level, g, f, r.id, MobRole.Role.BOSS, at);
        if (lord != null) {
            if (f.area == 0) g.boss = java.util.Optional.of(lord.getUUID());
            else g.annexBoss = java.util.Optional.of(lord.getUUID());
        } else {
            // the Lord can't take form (Peaceful): the gate yields rather than locking you in forever
            DirectorLog.log(p, "gate", "the Lord of gate " + g.id + " couldn't appear; the gate yields");
            Runs.lordDefeated(level, g, f, null, p);
            return;
        }
        if (r.normals > 0) Rooms.spawnGroup(level, g, f, r, r.id, r.normals, 0, 4);
        GateRegistry.get(level.getServer()).setDirty();
        boolean keeper = f.area == 1 && g.plan.is(Twist.GATE_WITHIN);
        String name = keeper ? "the inner gate's keeper" : f.area == 1 ? "the Lord's echo" : g.plan.theme().bossName;
        for (ServerPlayer o : level.players()) {
            RunState run = Runs.run(o);
            if (run.gateId != g.id || run.area != f.area || run.floor != f.floor) continue;
            var w = SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal(Rooms.capital(name)));
            int insight = Progression.profile(o).insight(g.plan.lordId());
            if (insight > 0 && !keeper) w.body(Component.literal("You've fallen to it " + insight + " time" + (insight > 1 ? "s" : "") + ". You know its tells now."));
            if (g.plan.is(Twist.INVERTED)) w.footer(Component.literal("The Lord sees straight through you."));
            Signal.send(o, w.priority(7).ttl(20 * 5).build());
        }
        level.playSound(null, at, GatesRegistry.SND_WARDEN.get(), SoundSource.HOSTILE, 2F, 0.6F);
    }

    /** Called by {@link Runs#lordDefeated} after the claims: open the way out (and the way on). */
    static void lordDefeated(ServerLevel level, GateRecord g, FloorLayout f, Room arena) {
        if (f.area == 0) {
            placePortal(level, g, f, arena, arena.feature(Room.FeatureKind.EXIT_ANCHOR), GatesRegistry.EXIT.get().defaultBlockState());
            if (g.plan.is(Twist.DOUBLE_GATE)) openRift(level, g, f, arena);
        } else if (g.plan.is(Twist.GATE_WITHIN)) {
            placePortal(level, g, f, arena, arena.feature(Room.FeatureKind.ANNEX_RETURN), GatesRegistry.RETURN.get().defaultBlockState());
        } else {
            placePortal(level, g, f, arena, arena.feature(Room.FeatureKind.EXIT_ANCHOR), GatesRegistry.EXIT.get().defaultBlockState());
        }
    }

    static void placePortal(ServerLevel level, GateRecord g, FloorLayout f, Room r, Room.Feature at, BlockState portal) {
        int cx = at == null ? r.centerX() : at.x(), cz = at == null ? r.centerZ() + 3 : at.z();
        // one opening, three wide and three tall, drawn as a single vortex in the gate's colours
        ((GateBlocks.Portal) portal.getBlock()).place(level, GateService.world(g, f, cx, r.dy, cz), Direction.Axis.X, 3, 3,
                g.plan.rank().ordinal());
        BlockPos c = GateService.world(g, f, cx, r.dy + 1, cz);
        level.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 40, 1, 1, 1, 0.05);
        level.playSound(null, c, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    static void openRift(ServerLevel level, GateRecord g, FloorLayout f, Room arena) {
        Room.Feature rift = arena.feature(Room.FeatureKind.RIFT_WALL);
        if (rift == null) return;
        // the near layer breaks away; the far layer becomes the rift, one opening drawn as a single red vortex
        Direction.Axis axis = rift.index() == 1 || rift.index() == 3 ? Direction.Axis.Z : Direction.Axis.X;
        GateBlocks.Portal portal = (GateBlocks.Portal) GatesRegistry.RIFT.get();
        int rank = g.plan.rank().ordinal();
        for (int[] b : FeatureBlocks.riftWall(arena, rift)) {
            BlockPos p = GateService.world(g, f, b[0], arena.dy + b[1], b[2]);
            boolean anchor = b[1] == 0 && (axis == Direction.Axis.X ? b[0] == rift.x() : b[2] == rift.z());
            level.setBlock(p, b[3] == 1 ? Blocks.AIR.defaultBlockState() : portal.opening(axis, rank, anchor), Block.UPDATE_ALL);
        }
        BlockPos c = GateService.world(g, f, rift.x(), arena.dy + 2, rift.z());
        level.playSound(null, c, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    // ---- portals: second gate, inner gate ---------------------------------------------------------------------------

    static void enterAnnex(ServerPlayer p, GateRecord g) {
        RunState run = Runs.run(p);
        if (run.gateId != g.id || run.area == 1) return;
        if (g.plan.is(Twist.GATE_WITHIN) && g.annexBossDead) {
            Signal.toast(p, Component.literal("The inner gate is quiet now."));
            return;
        }
        BlockPos e = GateService.entrance(g, 1, 1);
        ServerLevel lv = GateService.levelOf(p.level().getServer(), g, 1, 1);
        if (lv == null) lv = (ServerLevel) p.level();
        p.teleportTo(lv, e.getX() + 0.5, e.getY(), e.getZ() + 0.5, Set.of(), GateService.entranceYaw(g, 1, 1), 0, true);
        lv.playSound(null, e, GatesRegistry.SND_ENTER.get(), SoundSource.PLAYERS, 1F, 0.7F);
        Runs.arrive(p, g, 1, 1);
    }

    static void returnFromInner(ServerPlayer p, GateRecord g) {
        RunState run = Runs.run(p);
        if (run.gateId != g.id || run.area != 1) return;
        int floor = g.plan.innerGateFloor();
        FloorLayout f = GateLayout.of(g.plan).floor(0, floor);
        BlockPos to = GateService.entrance(g, 0, floor);
        if (f != null) {
            for (Room r : f.rooms) {
                Room.Feature ig = r.feature(Room.FeatureKind.INNER_GATE);
                if (ig != null) {
                    int dx = ig.index() == 1 ? (r.centerX() > ig.x() ? 2 : -2) : 0;
                    int dz = ig.index() == 0 ? (r.centerZ() > ig.z() ? 2 : -2) : 0;
                    to = GateService.roomFloor(g, f, r, ig.x() + dx, ig.z() + dz);
                }
            }
        }
        ServerLevel lv = GateService.levelOf(p.level().getServer(), f);
        p.teleportTo(lv != null ? lv : (ServerLevel) p.level(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, Set.of(), p.getYRot(), 0, true);
        Runs.arrive(p, g, 0, floor);
    }

    // ---- Friendly ---------------------------------------------------------------------------------------------------

    static void takeLantern(ServerPlayer p, GateRecord g, FloorLayout f, Room r, Room.Feature lantern, BlockPos pos) {
        FloorState fs = g.floor(f.area, f.floor);
        if (!fs.lanternsTaken.add(lantern.index())) return;
        ServerLevel level = (ServerLevel) p.level();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        Runs.run(p).lanterns++;
        int total = lanternsOn(f);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.2F);
        Signal.toast(p, Component.literal("A lost lantern (" + fs.lanternsTaken.size() + " of " + total + ")"));
        GateRegistry.get(level.getServer()).setDirty();
        Hud.send(p);
    }

    static void talkToSpirit(ServerPlayer p, GateRecord g, Entity spirit) {
        RunState run = Runs.run(p);
        FloorLayout f = GateLayout.of(g.plan).floor(run.area, run.floor);
        if (f == null) return;
        FloorState fs = g.floor(f.area, f.floor);
        ServerLevel level = (ServerLevel) p.level();
        if (fs.spiritDone) {
            Signal.toast(p, Component.literal("The spirit is at rest. The shrine is open."));
            return;
        }
        int total = lanternsOn(f);
        if (fs.lanternsTaken.size() < total) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("A lost spirit"))
                    .body(Component.literal("\"My lanterns went out somewhere in these halls. " + (total - fs.lanternsTaken.size())
                            + " still missing. I can't leave without them.\"")).ttl(20 * 6).build());
            return;
        }
        fs.spiritDone = true;
        Rooms.unsealShrine(level, g, f);
        for (ItemStack s : Loot.cache(g.plan.rank(), f.theme, p.getRandom(), false, 0.5)) Runs.satchelOrDrop(p, run, s);
        level.sendParticles(ParticleTypes.SOUL, spirit.getX(), spirit.getY() + 0.5, spirit.getZ(), 30, 0.4, 0.8, 0.4, 0.03);
        level.playSound(null, spirit.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 1F, 1F);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The spirit can rest"))
                .body(Component.literal("\"Thank you. Take what I kept. The shrine is open.\"")).ttl(20 * 6).build());
        spirit.discard();
        GateRegistry.get(level.getServer()).setDirty();
    }

    static void friendlyLord(ServerPlayer p, GateRecord g, Entity lord) {
        RunState run = Runs.run(p);
        FloorLayout f = GateLayout.of(g.plan).floor(run.area, run.floor);
        if (f == null || !f.bossFloor || g.bossDead) return;
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal(Rooms.capital(g.plan.theme().bossName)))
                .body(Component.literal("\"You came all this way without drawing a blade. The gate is yours.\"")).ttl(20 * 6).build());
        Runs.lordDefeated((ServerLevel) p.level(), g, f, null, p);
    }

    // ---- deaths of twist actors -------------------------------------------------------------------------------------

    static void onDeath(ServerLevel level, LivingEntity dead, ServerPlayer killer) {
        if (dead instanceof GateHoarder h && h.gateId >= 0) {
            GateRecord g = GateRegistry.get(level.getServer()).gate(h.gateId);
            if (g == null) return;
            FloorLayout f = GateLayout.of(g.plan).floor(h.area, h.floor);
            List<ItemStack> hoard = Loot.treasure(g.plan.rank(), f == null ? g.plan.theme() : f.theme, level.getRandom());
            for (ItemStack s : hoard) {
                if (killer != null && Runs.run(killer).gateId == g.id) Runs.satchelAdd(killer, s);
                if (!s.isEmpty()) level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, h.getX(), h.getY() + 0.5, h.getZ(), s));
            }
            level.sendParticles(ParticleTypes.WAX_ON, h.getX(), h.getY() + 0.5, h.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
            if (killer != null) {
                Signal.toast(killer, Component.literal("You caught the hoarder."));
                Progression.bestMoment(killer, "Caught a Hoarder in " + g.plan.theme().displayName);
            }
        }
    }
}
