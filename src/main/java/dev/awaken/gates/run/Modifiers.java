package dev.awaken.gates.run;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.entity.GateShade;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import dev.awaken.gates.world.RoomGeometry;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The 14 modifiers' behaviour at run time (Lightless and Swarm/Elite Pack/Fortune counts also act at build time). */
public final class Modifiers {
    private Modifiers() {}

    private static final Identifier LOW_GRAVITY = Awaken.id("gate_low_gravity");
    private static final Identifier LOW_GRAVITY_FALL = Awaken.id("gate_low_gravity_fall");
    private static final Identifier SWARM_HEALTH = Awaken.id("gate_swarm_health");

    // ---- players entering / leaving ---------------------------------------------------------------------------------

    static void applyTo(ServerPlayer p, GateRecord g) {
        if (g.plan.has(Modifier.LOW_GRAVITY)) lowGravity(p, true);
    }

    static void removeFrom(ServerPlayer p) {
        lowGravity(p, false);
    }

    private static void lowGravity(LivingEntity e, boolean on) {
        AttributeInstance grav = e.getAttribute(Attributes.GRAVITY);
        AttributeInstance fall = e.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (on) {
            var g = new AttributeModifier(LOW_GRAVITY, -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            var fl = new AttributeModifier(LOW_GRAVITY_FALL, 8, AttributeModifier.Operation.ADD_VALUE);
            // monsters keep it through a reload (saved); players get it back from the run tick and lose it on the way out
            boolean keep = !(e instanceof net.minecraft.world.entity.player.Player);
            if (grav != null) {
                if (keep) grav.addOrReplacePermanentModifier(g);
                else grav.addOrUpdateTransientModifier(g);
            }
            if (fall != null) {
                if (keep) fall.addOrReplacePermanentModifier(fl);
                else fall.addOrUpdateTransientModifier(fl);
            }
        } else {
            if (grav != null) grav.removeModifier(LOW_GRAVITY);
            if (fall != null) fall.removeModifier(LOW_GRAVITY_FALL);
        }
    }

    /** Spawn-time effects on a gate monster (never changes a monster that already exists: patent rule). */
    static void onSpawn(GateRecord g, LivingEntity e) {
        if (g.plan.has(Modifier.LOW_GRAVITY)) lowGravity(e, true);
        if (g.plan.has(Modifier.SWARM)) {
            AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) {
                hp.addOrReplacePermanentModifier(new AttributeModifier(SWARM_HEALTH, -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                e.setHealth(e.getMaxHealth());
            }
        }
    }

    // ---- No Regen ---------------------------------------------------------------------------------------------------

    private static final Set<UUID> HEAL_OK = new HashSet<>();

    /** The next heal of this player is allowed even under No Regen (the shrine's rest, Cheat Death). */
    static void allowHeal(ServerPlayer p) {
        HEAL_OK.add(p.getUUID());
    }

    static void onHeal(LivingHealEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (HEAL_OK.remove(p.getUUID())) return;
        GateRecord g = Runs.gateOf(p);
        if (g != null && g.plan.has(Modifier.NO_REGEN) && e.getAmount() <= 1.0F) e.setCanceled(true);
    }

    // ---- damage: Glass, damage tracking, Silence noise --------------------------------------------------------------

    static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide() || !GateService.inGate(victim)) return;
        MinecraftServer server = victim.level().getServer();
        if (victim instanceof ServerPlayer p) {
            GateRecord g = Runs.gateOf(p);
            if (g == null) return;
            if (g.plan.has(Modifier.GLASS)) e.setAmount(e.getAmount() * 2);
            RunState run = Runs.run(p);
            run.damageFloor += e.getAmount();
            run.damageRun += e.getAmount();
            run.noise += 3;
            return;
        }
        if (victim instanceof dev.awaken.gates.entity.GateHoarder h && e.getSource().getEntity() instanceof ServerPlayer attacker) {
            GateRecord hg = GateRegistry.get(server).gate(h.gateId);
            if (hg != null) Twists.onPlayerHits(attacker, hg, victim, e);
            return;
        }
        MobRole role = victim.getData(CoreApi.MOB_ROLE.get());
        if (role.gateId().isEmpty()) return;
        GateRecord g;
        try {
            g = GateRegistry.get(server).gate(Integer.parseInt(role.gateId()));
        } catch (NumberFormatException ex) {
            return;
        }
        if (g == null) return;
        if (g.plan.has(Modifier.GLASS)) e.setAmount(e.getAmount() * 2);
        Entity src = e.getSource().getEntity();
        if (src instanceof ServerPlayer attacker) {
            Runs.run(attacker).noise += 4;
            Twists.onPlayerHits(attacker, g, victim, e);
        }
    }

    /** Fortune doubles what gate monsters drop. */
    static void onDrops(LivingDropsEvent e) {
        LivingEntity dead = e.getEntity();
        if (!GateService.inGate(dead)) return;
        MobRole role = dead.getData(CoreApi.MOB_ROLE.get());
        if (role.gateId().isEmpty()) return;
        GateRecord g = gate(dead.level().getServer(), role);
        if (g == null || !g.plan.has(Modifier.FORTUNE)) return;
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity ie : e.getDrops()) extra.add(new ItemEntity(dead.level(), ie.getX(), ie.getY(), ie.getZ(), ie.getItem().copy()));
        e.getDrops().addAll(extra);
    }

    static GateRecord gate(MinecraftServer server, MobRole role) {
        try {
            return GateRegistry.get(server).gate(Integer.parseInt(role.gateId()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ---- Volatile and Echoing: what happens when a gate monster dies -----------------------------------------------

    private record Pending(long at, Runnable run) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    static void onMobDeath(ServerLevel level, GateRecord g, LivingEntity dead, MobRole role) {
        long now = GateService.now(level.getServer());
        if (Forces.has(dead, Forces.Tag.VOLATILE) && role.role() != MobRole.Role.BOSS) {
            double x = dead.getX(), y = dead.getY(), z = dead.getZ();
            level.sendParticles(ParticleTypes.SMOKE, x, y + 0.6, z, 20, 0.3, 0.4, 0.3, 0.02);
            level.playSound(null, x, y, z, SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1F, 1.2F);
            PENDING.add(new Pending(now + 20, () -> level.explode(null, level.damageSources().explosion(null, null), null, x, y + 0.5, z, 2.2F, false, Level.ExplosionInteraction.NONE)));
        }
        // Awaken creatures rise by themselves (they fall instead of dying); this brings back anything else once.
        if (Forces.has(dead, Forces.Tag.ECHO) && !Forces.hasRisen(dead)
                && (role.role() == MobRole.Role.NORMAL || role.role() == MobRole.Role.ELITE)) {
            GateMobs.Key key = GateMobs.keyOf(dead);
            BlockPos at = dead.blockPosition();
            level.sendParticles(ParticleTypes.SCULK_SOUL, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
            PENDING.add(new Pending(now + 40, () -> {
                GateRecord gg = GateRegistry.get(level.getServer()).gate(g.id);
                if (gg == null || gg.state == dev.awaken.gates.GateState.RELEASED) return;
                FloorLayout f = key == null ? null : GateLayout.of(gg.plan).floor(key.area(), key.floor());
                if (f == null) return;
                LivingEntity risen = Rooms.spawn(level, gg, f, key.room(), role.role(), at);
                if (risen != null) {
                    Forces.markRisen(risen);
                    level.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 16, 0.3, 0.6, 0.3, 0.02);
                    level.playSound(null, at, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 0.7F, 0.6F);
                }
            }));
        }
    }

    static void tickPending(MinecraftServer server) {
        tickWater(server); // Flooding fills on its own clock, not only when a burst or a rise is pending
        if (PENDING.isEmpty()) return;
        long now = GateService.now(server);
        List<Pending> due = new ArrayList<>();
        PENDING.removeIf(pd -> {
            if (pd.at <= now) {
                due.add(pd);
                return true;
            }
            return false;
        });
        for (Pending pd : due) pd.run.run();
    }

    /** Test hook: runs every scheduled effect now, due or not. */
    static void runAllPending(MinecraftServer server) {
        List<Pending> all = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Pending pd : all) pd.run.run();
        tickWater(server);
    }

    // ---- per player -------------------------------------------------------------------------------------------------

    private static final Map<UUID, String> SHADE_FLOOR = new HashMap<>();
    private static final Map<UUID, Long> LISTENER_AT = new HashMap<>();

    static void tickPlayer(ServerPlayer p, GateRecord g, RunState run, FloorLayout f, Room r) {
        ServerLevel level = (ServerLevel) p.level();
        long now = GateService.now(level.getServer());
        if (g.plan.has(Modifier.SILENCE) && !g.plan.friendly()) {
            if (p.isSprinting()) run.noise += 2;
            else if (p.isShiftKeyDown()) run.noise = Math.max(0, run.noise - 3);
            else run.noise = Math.max(0, run.noise - 1);
            if (!p.onGround() && p.getDeltaMovement().y > 0.2) run.noise += 1;
            Long lastListener = LISTENER_AT.get(p.getUUID());
            if (run.noise >= 24 && (lastListener == null || now - lastListener > 600) && r != null && !r.type.safe() && f != null) {
                BlockPos at = Pressure.spotNear(level, g, f, p, 9);
                if (at != null) {
                    LivingEntity l = Rooms.spawn(level, g, f, -1, MobRole.Role.ELITE, at);
                    if (l != null) {
                        LISTENER_AT.put(p.getUUID(), now);
                        run.noise = 6;
                        l.setCustomName(Component.literal("Listener"));
                        var fr = l.getAttribute(Attributes.FOLLOW_RANGE);
                        if (fr != null) fr.setBaseValue(Math.max(fr.getBaseValue(), 40));
                        if (l instanceof net.minecraft.world.entity.Mob m) m.setTarget(p);
                    }
                    level.playSound(null, at, SoundEvents.WARDEN_LISTENING_ANGRY, SoundSource.HOSTILE, 1.3F, 1.1F);
                    Signal.toast(p, Component.literal("Something heard you."));
                }
            }
        }
        if (g.plan.has(Modifier.MIRROR) && !g.plan.friendly() && f != null) {
            String key = g.id + ":" + run.area + ":" + run.floor + ":" + run.runIndex;
            if (!key.equals(SHADE_FLOOR.get(p.getUUID())) && now - run.floorStartedAt > 40 * 20 && r != null && !r.type.safe()) {
                BlockPos at = Pressure.spotNear(level, g, f, p, 8);
                if (at != null) {
                    GateShade shade = GatesRegistry.SHADE.get().create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                    if (shade != null) {
                        SHADE_FLOOR.put(p.getUUID(), key);
                        boolean glass = g.plan.has(Modifier.GLASS);
                        double dmg = p.getAttributeValue(Attributes.ATTACK_DAMAGE) * (glass ? 1.0 : 0.6);
                        shade.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot() + 180, 0);
                        shade.bind(p, p.getMaxHealth() * (glass ? 1.0 : 0.8), dmg);
                        level.addFreshEntity(shade);
                        GateMobs.register(shade, new GateMobs.Key(g.id, run.area, run.floor, -1));
                        level.playSound(null, at, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.2F, 0.7F);
                        Signal.toast(p, Component.literal("Your shadow steps out of the wall."));
                    }
                }
            }
        }
    }

    // ---- per gate: Flooding, Haunted, Collapse ---------------------------------------------------------------------

    private static final Map<String, Long> LAST = new HashMap<>();

    public static long collapseTicks(GateRecord g) {
        return Math.max(4 * 60, 7 * 60 - g.plan.rank().ordinal() * 30) * 20L;
    }

    static void tickGate(ServerLevel level, GateRecord g) {
        long now = GateService.now(level.getServer());
        Map<String, List<ServerPlayer>> onFloor = new HashMap<>();
        for (ServerPlayer p : level.players()) {
            RunState run = Runs.run(p);
            if (run.gateId == g.id && p.isAlive()) onFloor.computeIfAbsent(run.area + ":" + run.floor, k -> new ArrayList<>()).add(p);
        }
        for (var e : onFloor.entrySet()) {
            List<ServerPlayer> players = e.getValue();
            RunState first = Runs.run(players.get(0));
            FloorLayout f = GateLayout.of(g.plan).floor(first.area, first.floor);
            if (f == null) continue;
            FloorState fs = g.floor(first.area, first.floor);
            if (g.plan.has(Modifier.FLOODING)) flood(level, g, f, fs, players, now);
            if (g.plan.has(Modifier.HAUNTED) && !g.plan.friendly()) {
                String k = "haunt:" + g.id + ":" + e.getKey();
                if (now - LAST.getOrDefault(k, now - 600) >= 80 * 20) {
                    LAST.put(k, now);
                    ServerPlayer target = players.get(level.getRandom().nextInt(players.size()));
                    BlockPos at = Pressure.spotNear(level, g, f, target, 6);
                    if (at != null && Rooms.spawn(level, g, f, -1, MobRole.Role.SPIRIT, at) != null) {
                        level.sendParticles(ParticleTypes.SOUL, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 20, 0.4, 0.8, 0.4, 0.01);
                        Signal.toast(target, Component.literal("A cold presence."));
                    }
                }
            }
            if (g.plan.has(Modifier.COLLAPSE_TIMER) && fs.entered()) collapse(level, g, f, fs, players, now);
        }
    }

    private static void collapse(ServerLevel level, GateRecord g, FloorLayout f, FloorState fs, List<ServerPlayer> players, long now) {
        long left = collapseTicks(g) - (now - fs.enteredAt);
        if (left <= 60 * 20 && left > 59 * 20) {
            for (ServerPlayer p : players) Signal.toast(p, Component.literal("The ceiling groans. One minute."));
        }
        if (left > 0) return;
        if (!fs.collapsed) {
            fs.collapsed = true;
            GateRegistry.get(level.getServer()).setDirty();
            for (ServerPlayer p : players) Signal.toast(p, Component.literal("The floor is coming down."));
        }
        if (level.getServer().getTickCount() % 40 != 0) return;
        // the ceiling really comes down (the Gate Rig's cave-in): blocks fall round everyone still on the floor
        for (ServerPlayer p : players) {
            RunState run = Runs.run(p);
            if (run.clearedFloor(run.area, run.floor)) continue;
            Where w = Where.at(level.getServer(), p.blockPosition());
            if (w == null || w.room() == null || w.room().type.safe()) continue;
            level.playSound(null, p.blockPosition(), GatesRegistry.SND_COLLAPSE.get(), SoundSource.BLOCKS, 1F, 0.8F);
            RigCaveIn.wave(level, g, f, w.room(), p);
        }
    }

    // ---- Flooding ---------------------------------------------------------------------------------------------------

    /** Water to pour, each in the level its floor lives in. */
    private record Pour(ServerLevel level, BlockPos at) {}
    private static final ArrayDeque<Pour> WATER = new ArrayDeque<>();

    /** Server start/stop: pending bursts and queued water hold the old world's levels; gate ids restart per world. */
    public static void resetAll() {
        HEAL_OK.clear();
        PENDING.clear();
        SHADE_FLOOR.clear();
        LISTENER_AT.clear();
        LAST.clear();
        WATER.clear();
    }
    private static final int WATER_PER_TICK = 256;

    private static void flood(ServerLevel level, GateRecord g, FloorLayout f, FloorState fs, List<ServerPlayer> players, long now) {
        for (ServerPlayer p : players) {
            Where w = Where.at(level.getServer(), p.blockPosition());
            if (w == null || w.room() == null) continue;
            Room r = w.room();
            if (r.type.safe() || r.height < 6 || r.shape == Room.Shape.SHAFT || r.shape == Room.Shape.ISLANDS) continue;
            String k = "flood:" + g.id + ":" + f.area + ":" + f.floor + ":" + r.id;
            int levelNow = fs.water.getOrDefault(r.id, 0);
            if (levelNow >= 3) continue;
            if (now - LAST.getOrDefault(k, now) < 20 * 20) {
                LAST.putIfAbsent(k, now);
                continue;
            }
            LAST.put(k, now);
            int next = levelNow + 1;
            fs.water.put(r.id, next);
            GateRegistry.get(level.getServer()).setDirty();
            int y = next - 1;
            for (int x = r.x0(); x <= r.x1(); x++) {
                for (int z = r.z0(); z <= r.z1(); z++) {
                    if (!RoomGeometry.inside(r, x, z) || RoomGeometry.solid(r, x, z)) continue;
                    WATER.add(new Pour(level, GateService.roomFloor(g, f, r, x, z).above(y + RoomGeometry.floorOffset(r, x, z))));
                }
            }
            Signal.toast(p, Component.literal(next == 1 ? "Water seeps in." : next == 2 ? "The water is rising." : "Swim."));
        }
    }

    private static void tickWater(MinecraftServer server) {
        if (WATER.isEmpty()) return;
        for (int i = 0; i < WATER_PER_TICK && !WATER.isEmpty(); i++) {
            Pour w = WATER.poll();
            ServerLevel lv = w.level();
            BlockPos p = w.at();
            if (lv.isLoaded(p) && lv.getBlockState(p).isAir()) lv.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    static void forget(UUID player) {
        SHADE_FLOOR.remove(player);
        LISTENER_AT.remove(player);
        HEAL_OK.remove(player);
    }
}
