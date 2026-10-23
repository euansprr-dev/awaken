package dev.awaken.descent.rules;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.descent.Descent;
import dev.awaken.descent.DescentContent;
import dev.awaken.descent.DescentModule;
import dev.awaken.descent.DescentSync;
import dev.awaken.descent.Strata;
import dev.awaken.descent.entity.ShadowSelf;
import dev.awaken.descent.player.DescentData;
import dev.awaken.descent.spawn.DescentSpawner;
import dev.awaken.descent.world.Layout;
import dev.awaken.descent.world.Terrain;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The rule engine. A zone is one depth (a stratum, or one Endless cell) with players in it. Zones tick every server
 * tick while occupied and run their rules, the spawner, the guardian and the HUD. All per-tick work reuses objects.
 */
public final class Zones {
    private Zones() {}

    public static final Identifier TIDE_MOD = dev.awaken.Awaken.id("descent_tide");

    /** Per-player rule state (noise meter, shadow timer, tide landing). */
    public static final class PState {
        public float noise;
        int lastNoiseSent = -10;
        boolean heardToasted;
        long nextShadow;
        UUID shadow;
        boolean tideAirborne;
        int zoneDepth;
        long lastHud;
    }

    static final class Zone {
        final int depth, rules, form;
        final Terrain terrain;
        final DescentSpawner.Tracked tracked = new DescentSpawner.Tracked();
        final List<ServerPlayer> players = new ArrayList<>();
        long age;
        // wind
        long nextGust = 240, gustEnd;
        float gustX, gustZ;
        // tides
        int tidePhase = -1;
        // flood
        boolean floodWarned;
        // guardian
        UUID guardian;
        long lastGuardian = -1_000_000;
        // finale
        UUID finale;
        final DreamRooms.Watch dream = new DreamRooms.Watch();
        final List<long[]> rises = new ArrayList<>(); // x, y, z, at-tick

        Zone(int depth, long seed) {
            this.depth = depth;
            this.rules = Strata.rulesOf(depth);
            this.form = Strata.formOf(depth);
            this.terrain = Terrain.forDepth(depth, seed);
        }

        boolean has(int rule) {
            return Strata.has(rules, rule);
        }
    }

    private static final Int2ObjectOpenHashMap<Zone> ZONES = new Int2ObjectOpenHashMap<>();
    private static final Map<UUID, PState> PLAYERS = new HashMap<>();
    private static final List<Zone> ACTIVE = new ArrayList<>();

    public static PState state(ServerPlayer p) {
        return PLAYERS.computeIfAbsent(p.getUUID(), k -> new PState());
    }

    public static void forget(UUID id) {
        PLAYERS.remove(id);
    }

    public static void clear() {
        ZONES.clear();
        PLAYERS.clear();
    }

    static Zone zone(ServerLevel level, int depth) {
        Zone z = ZONES.get(depth);
        if (z == null) {
            z = new Zone(depth, level.getSeed());
            ZONES.put(depth, z);
        }
        return z;
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (!DescentModule.enabled()) return;
        for (Zone z : ZONES.values()) z.players.clear();
        for (ServerLevel level : server.getAllLevels()) {
            if (!Strata.isDescent(level.dimension()) || level.players().isEmpty()) continue;
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator()) continue;
                int depth = Strata.depthAt(level.dimension(), p.blockPosition());
                if (depth <= 0) continue;
                Zone z = zone(level, depth);
                z.players.add(p);
                PState ps = state(p);
                if (ps.zoneDepth != depth) enter(p, ps, z, level.getGameTime());
            }
        }
        // tick a snapshot: rules may create zones (a mob dying elsewhere) while we iterate
        ACTIVE.clear();
        for (Zone z : ZONES.values()) if (!z.players.isEmpty()) ACTIVE.add(z);
        for (Zone z : ACTIVE) tickZone(z.players.getFirst().level(), z);
    }

    private static void enter(ServerPlayer p, PState ps, Zone z, long now) {
        ps.zoneDepth = z.depth;
        ps.noise = 0;
        ps.tideAirborne = false;
        ps.nextShadow = now + 1200 + p.getRandom().nextInt(1200);
        if (!z.has(Strata.RULE_TIDES)) setTide(p, 0);
    }

    /** Called when a player leaves the Descent (dimension change, logout, death). */
    public static void left(ServerPlayer p) {
        PState ps = PLAYERS.get(p.getUUID());
        if (ps != null) ps.zoneDepth = 0;
        setTide(p, 0);
        DescentSync.hud(p, 0, Component.empty(), -1, 0);
    }

    private static void tickZone(ServerLevel level, Zone z) {
        z.age++;
        long now = level.getGameTime();
        RandomSource rnd = level.getRandom();
        if (z.has(Strata.RULE_WIND)) wind(level, z, rnd);
        if (z.has(Strata.RULE_FLOOD)) flood(level, z);
        if (z.has(Strata.RULE_TIDES)) tides(level, z);
        if (z.has(Strata.RULE_LISTENERS)) listeners(level, z, now, rnd);
        if (z.has(Strata.RULE_ROOMS) && DreamRooms.applies(z.depth)) DreamRooms.tick(level, z.terrain, z.depth, z.dream, z.players, now, rnd);
        if (z.has(Strata.RULE_SHADOW) && now % 20 == 0) shadows(level, z, now, rnd);
        if (!z.rises.isEmpty()) rises(level, z, now);
        if (now % 40 == 0) {
            spawn(level, z, rnd);
            guardian(level, z, now);
            if (z.depth == Strata.COUNT) seedApproach(level, z);
        }
        if (now % 200 == 0) despawn(z);
        if (now % 20 == 0) {
            for (ServerPlayer p : z.players) hud(p, z, now);
        } else if (z.has(Strata.RULE_LISTENERS) && now % 5 == 0) {
            for (ServerPlayer p : z.players) {
                if (Math.abs(Math.round(state(p).noise) - state(p).lastNoiseSent) >= 2) hud(p, z, now);
            }
        }
    }

    // ---- rule 2: wind -----------------------------------------------------------------------------------------------

    private static void wind(ServerLevel level, Zone z, RandomSource rnd) {
        long t = z.age;
        if (t == z.nextGust - 30) {
            double a = rnd.nextDouble() * Math.PI * 2;
            z.gustX = (float) Math.cos(a);
            z.gustZ = (float) Math.sin(a);
            for (ServerPlayer p : z.players) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), DescentContent.SND_WIND.get(), SoundSource.AMBIENT, 1.0F, 0.8F + rnd.nextFloat() * 0.3F);
            }
        }
        if (t == z.nextGust) z.gustEnd = t + 50;
        if (t >= z.nextGust && t <= z.gustEnd) {
            if (t % 4 == 0) {
                for (ServerPlayer p : z.players) {
                    if (p.getAbilities().flying) continue;
                    level.sendParticles(p, ParticleTypes.CLOUD, true, false, p.getX() - z.gustX * 4, p.getY() + 1, p.getZ() - z.gustZ * 4,
                            6, 1.5, 0.8, 1.5, 0.05);
                    if (!nearLedge(level, p, z.gustX, z.gustZ)) continue;
                    double force = p.isShiftKeyDown() ? 0.025 : 0.13;
                    Vec3 v = p.getDeltaMovement();
                    p.setDeltaMovement(v.x + z.gustX * force, v.y, v.z + z.gustZ * force);
                    p.syncVelocity = true;
                }
            }
            if (t == z.gustEnd) z.nextGust = t + 160 + rnd.nextInt(200);
        }
    }

    /** True when there's a drop of at least 7 blocks within 4.5 blocks downwind. */
    static boolean nearLedge(ServerLevel level, ServerPlayer p, float wx, float wz) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (double d = 1.5; d <= 4.6; d += 1.5) {
            int x = (int) Math.floor(p.getX() + wx * d), z = (int) Math.floor(p.getZ() + wz * d);
            int y = (int) Math.floor(p.getY()) - 1;
            boolean open = true;
            for (int k = 0; k < 7 && open; k++) {
                m.set(x, y - k, z);
                if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) open = false;
            }
            if (open) return true;
        }
        return false;
    }

    // ---- rule 3: rising water ---------------------------------------------------------------------------------------

    private static void flood(ServerLevel level, Zone z) {
        long c = Math.floorMod(z.age, Flood.CYCLE);
        Flood.Data data = Flood.data(level);
        if (c == Flood.WARN) {
            for (ServerPlayer p : z.players) {
                level.playSound(null, p.getX(), p.getY(), p.getZ(), DescentContent.SND_CHOIR.get(), SoundSource.AMBIENT, 1.2F, 1.0F);
                Signal.toast(p, Component.literal("The Choir is singing. The water will rise.").withStyle(ChatFormatting.AQUA));
            }
        }
        if (c == Flood.RISE) {
            for (ServerPlayer p : z.players) level.playSound(null, p.getX(), p.getY(), p.getZ(), DescentContent.SND_FLOOD.get(), SoundSource.AMBIENT, 1.0F, 0.8F);
        }
        if (c >= Flood.RISE && c < Flood.DRAIN) {
            if (z.age % 4 == 0) for (ServerPlayer p : z.players) Flood.fill(level, p, z.depth, z.age, data);
        } else if (!data.placed.isEmpty()) {
            int interval = c >= Flood.DRAIN ? 30 : 10;
            if (z.age % interval == 0) Flood.drainLayer(level, data);
        }
    }

    // ---- rule 7: gravity tides --------------------------------------------------------------------------------------

    public static final int TIDE_CYCLE = 2400, TIDE_SWELL = 1600, TIDE_INVERT = 1700, TIDE_EBB = 2100, TIDE_CALM = 2220;

    /** 0 calm, 1 swell (warning), 2 inverted, 3 ebb. Pure. */
    public static int tidePhase(long age) {
        long c = Math.floorMod(age, TIDE_CYCLE);
        if (c < TIDE_SWELL || c >= TIDE_CALM) return 0;
        if (c < TIDE_INVERT) return 1;
        if (c < TIDE_EBB) return 2;
        return 3;
    }

    private static void tides(ServerLevel level, Zone z) {
        int phase = tidePhase(z.age);
        boolean changed = phase != z.tidePhase;
        z.tidePhase = phase;
        if (changed) {
            for (ServerPlayer p : z.players) {
                PState ps = state(p);
                if (phase == 1) Signal.toast(p, Component.literal("The tide is turning.").withStyle(ChatFormatting.AQUA));
                if (phase == 2) level.playSound(null, p.getX(), p.getY(), p.getZ(), DescentContent.SND_TIDE.get(), SoundSource.AMBIENT, 1.0F, 1.4F);
                if (phase == 3) {
                    ps.tideAirborne = !p.onGround() && !p.isInWater();
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), DescentContent.SND_TIDE.get(), SoundSource.AMBIENT, 1.0F, 0.7F);
                    Signal.toast(p, Component.literal("The tide ebbs. Find the ground."));
                }
                if (phase == 0) ps.tideAirborne = false;
            }
        }
        if (z.age % 5 != 0) return;
        for (ServerPlayer p : z.players) {
            setTide(p, phase);
            if (phase >= 2) p.resetFallDistance();
            PState ps = state(p);
            if (phase == 3 && ps.tideAirborne && p.onGround()) {
                ps.tideAirborne = false;
                dev.awaken.progression.Progression.count(p, "awaken:latent/inverted_landing");
                Signal.toast(p, Component.literal("You landed upright."));
            }
        }
    }

    static void setTide(ServerPlayer p, int phase) {
        AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
        if (g == null) return;
        double amount = phase == 2 ? -1.06 : phase == 3 ? -0.85 : 0;
        if (amount == 0) {
            if (g.hasModifier(TIDE_MOD)) g.removeModifier(TIDE_MOD);
            return;
        }
        AttributeModifier cur = g.getModifier(TIDE_MOD);
        if (cur == null || cur.amount() != amount) {
            g.addOrUpdateTransientModifier(new AttributeModifier(TIDE_MOD, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    // ---- rule 5: the Listeners --------------------------------------------------------------------------------------

    public static final float HEARD = 35, HUNTED = 70;

    private static void listeners(ServerLevel level, Zone z, long now, RandomSource rnd) {
        for (ServerPlayer p : z.players) {
            PState ps = state(p);
            boolean sneaking = p.isShiftKeyDown();
            double speed = p.getDeltaMovement().horizontalDistanceSqr();
            float n = ps.noise - (sneaking ? 0.6F : 0.3F);
            if (!sneaking) {
                if (p.isSprinting() && speed > 0.01) n += 0.9F;
                else if (speed > 0.002) n += 0.25F;
            }
            ps.noise = Math.max(0, Math.min(100, n));
        }
        if (now % 20 != 0) return;
        for (ServerPlayer p : z.players) {
            PState ps = state(p);
            if (ps.noise < HEARD) continue;
            DescentData d = DescentSync.data(p);
            if (!d.visitHeard) d.visitHeard = true;
            if (!ps.heardToasted) {
                ps.heardToasted = true;
                level.playSound(null, p.getX(), p.getY(), p.getZ(), DescentContent.SND_LISTENER.get(), SoundSource.HOSTILE, 1.0F, 0.6F);
                Signal.toast(p, Component.literal("Something heard you.").withStyle(ChatFormatting.RED));
            }
            boolean hunt = ps.noise >= HUNTED;
            int listenersNear = 0;
            for (LivingEntity m : z.tracked.mobs) {
                if (!(m instanceof Mob mob) || !DescentSpawner.has(m, DescentSpawner.FLAG_LISTENER) || m.level() != level) continue;
                double d2 = m.distanceToSqr(p);
                if (d2 < 40 * 40) listenersNear++;
                if (d2 > 36 * 36) continue;
                if (hunt) mob.setTarget(p);
                else if (mob.getTarget() == null) mob.getNavigation().moveTo(p.getX(), p.getY(), p.getZ(), 1.0);
            }
            if (ps.noise >= 55 && listenersNear < 4 && rnd.nextInt(3) == 0 && z.tracked.mobs.size() < DescentSpawner.ZONE_CAP) {
                DescentSpawner.trySpawn(level, p, z.depth, z.rules, z.tracked, true);
            }
        }
    }

    /** A loud action (attack, break, getting hurt). */
    public static void noise(ServerPlayer p, float amount) {
        PState ps = PLAYERS.get(p.getUUID());
        if (ps == null || ps.zoneDepth <= 0 || !Strata.has(Strata.rulesOf(ps.zoneDepth), Strata.RULE_LISTENERS)) return;
        if (p.isShiftKeyDown()) amount *= 0.5F;
        ps.noise = Math.min(100, ps.noise + amount);
    }

    /** Listeners only lock onto players they can hear (or who hurt them). */
    public static boolean listenerMayTarget(Mob mob, ServerPlayer p) {
        PState ps = PLAYERS.get(p.getUUID());
        if (ps == null || ps.noise >= HEARD) return true;
        return mob.getLastHurtByMob() == p;
    }

    // ---- rule 4: the dead rise once ---------------------------------------------------------------------------------

    public static void onMobDeath(ServerLevel level, LivingEntity dead) {
        int depth = Strata.depthAt(level.dimension(), dead.blockPosition());
        if (depth <= 0 || !Strata.has(Strata.rulesOf(depth), Strata.RULE_RISE)) return;
        if (!(dead instanceof Enemy) || DescentSpawner.has(dead, DescentSpawner.FLAG_RISEN)
                || dev.awaken.core.Forces.has(dead, dev.awaken.core.Forces.Tag.ECHO)) return;
        MobRole role = dead.getData(CoreApi.MOB_ROLE.get());
        if (role.role() == MobRole.Role.BOSS) return;
        Zone z = zone(level, depth);
        if (z.rises.size() > 16) return;
        BlockPos p = dead.blockPosition();
        z.rises.add(new long[]{p.getX(), p.getY(), p.getZ(), level.getGameTime() + 60});
    }

    private static void rises(ServerLevel level, Zone z, long now) {
        for (int i = z.rises.size() - 1; i >= 0; i--) {
            long[] r = z.rises.get(i);
            BlockPos pos = new BlockPos((int) r[0], (int) r[1], (int) r[2]);
            if (now < r[3]) {
                if (now % 5 == 0) level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0.01);
                continue;
            }
            z.rises.remove(i);
            z.tracked.prune();
            if (z.tracked.mobs.size() >= DescentSpawner.ZONE_CAP) continue;
            LivingEntity mob = DescentSpawner.spawnRisen(level, pos, z.depth, z.tracked);
            if (mob != null) {
                level.playSound(null, pos, DescentContent.SND_RISE.get(), SoundSource.HOSTILE, 1.0F, 0.8F);
                level.sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
            }
        }
    }

    // ---- rule 6: machines wake --------------------------------------------------------------------------------------

    static void machineWoke(ServerLevel level, ServerPlayer p, int depth, BlockPos at) {
        Zone z = zone(level, depth);
        z.tracked.prune();
        if (z.tracked.mobs.size() >= DescentSpawner.ZONE_CAP || level.getRandom().nextInt(3) != 0) return;
        LivingEntity m = DescentSpawner.trySpawn(level, p, depth, z.rules, z.tracked, false);
        if (m != null) DescentSpawner.flag(m, DescentSpawner.FLAG_SENTRY);
    }

    // ---- rule 9: your shadow ----------------------------------------------------------------------------------------

    private static void shadows(ServerLevel level, Zone z, long now, RandomSource rnd) {
        for (ServerPlayer p : z.players) {
            PState ps = state(p);
            if (ps.shadow != null && level.getEntity(ps.shadow) instanceof ShadowSelf s && s.isAlive()) continue;
            ps.shadow = null;
            if (now < ps.nextShadow || p.isCreative()) continue;
            ps.nextShadow = now + 3600 + rnd.nextInt(1200);
            Vec3 back = p.getLookAngle().multiply(-1, 0, -1).normalize();
            BlockPos spot = null;
            for (int tries = 0; tries < 6 && spot == null; tries++) {
                double d = 6 + rnd.nextInt(5);
                double a = (rnd.nextDouble() - 0.5) * 1.2;
                double bx = back.x * Math.cos(a) - back.z * Math.sin(a), bz = back.x * Math.sin(a) + back.z * Math.cos(a);
                spot = DescentSpawner.findFloor(level, (int) Math.floor(p.getX() + bx * d), (int) Math.floor(p.getY()) + 3, (int) Math.floor(p.getZ() + bz * d));
            }
            if (spot == null) continue;
            ShadowSelf s = DescentContent.SHADOW_SELF.get().create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (s == null) continue;
            s.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, p.getYRot(), 0);
            s.mirror(p);
            level.addFreshEntity(s);
            ps.shadow = s.getUUID();
            level.sendParticles(ParticleTypes.LARGE_SMOKE, s.getX(), s.getY() + 1, s.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
            level.playSound(null, spot, DescentContent.SND_SHADOW.get(), SoundSource.HOSTILE, 1.0F, 0.7F);
            Signal.toast(p, Component.literal("Your shadow stands up.").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    // ---- spawning, guardians, the Seed ------------------------------------------------------------------------------

    private static void spawn(ServerLevel level, Zone z, RandomSource rnd) {
        z.tracked.prune();
        if (z.tracked.mobs.size() >= DescentSpawner.ZONE_CAP) return;
        int cap = DescentSpawner.perPlayerCap(z.depth);
        for (ServerPlayer p : z.players) {
            if (p.isCreative() || rnd.nextInt(10) >= 6) continue;
            if (z.tracked.near(p, 48) >= cap) continue;
            if (Descent.nearCamp(p, z.depth, 20)) continue;
            DescentSpawner.trySpawn(level, p, z.depth, z.rules, z.tracked, false);
            if (z.tracked.mobs.size() >= DescentSpawner.ZONE_CAP) return;
        }
    }

    private static void despawn(Zone z) {
        z.tracked.prune();
        for (LivingEntity m : z.tracked.mobs) {
            boolean guardian = DescentSpawner.has(m, DescentSpawner.FLAG_GUARDIAN) || DescentSpawner.has(m, DescentSpawner.FLAG_FINALE);
            double keep = guardian ? 160 : 96;
            boolean near = false;
            for (ServerPlayer p : z.players) if (p.distanceToSqr(m) < keep * keep) { near = true; break; }
            if (!near) m.discard();
        }
        z.tracked.prune();
    }

    private static void guardian(ServerLevel level, Zone z, long now) {
        if (z.form == Strata.COUNT) return;
        if (z.guardian != null && level.getEntity(z.guardian) instanceof LivingEntity g && g.isAlive()) return;
        z.guardian = null;
        BlockPos lair = Layout.world(z.depth, Layout.lair(z.form));
        for (ServerPlayer p : z.players) {
            if (p.isCreative()) continue;
            double dx = p.getX() - lair.getX(), dz = p.getZ() - lair.getZ();
            if (dx * dx + dz * dz > 34 * 34 || Math.abs(p.getY() - lair.getY()) > 14) continue;
            boolean beaten = DescentSync.data(p).guardians.contains(z.depth);
            if (beaten && now - z.lastGuardian < 48000) continue;
            LivingEntity boss = DescentSpawner.spawnGuardian(level, z.depth, z.tracked);
            if (boss == null) return;
            z.guardian = boss.getUUID();
            z.lastGuardian = now;
            for (ServerPlayer q : z.players) {
                if (q.distanceToSqr(boss) > 96 * 96) continue;
                Signal.send(q, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("The Guardian wakes"))
                        .body(Component.literal("Something that keeps " + Strata.shortName(z.depth) + " stands between you and the pit."))
                        .ttl(20 * 6).priority(4).build());
            }
            return;
        }
    }

    private static void seedApproach(ServerLevel level, Zone z) {
        if (dev.awaken.core.AwakenConfig.STORY.get()) return; // the story module runs the finale
        BlockPos arena = Layout.world(z.depth, Layout.pit(z.form));
        for (ServerPlayer p : z.players) {
            if (DescentSync.data(p).seedCleared) continue;
            if (p.blockPosition().distSqr(arena) < 26 * 26) {
                Descent.beginFinale(p);
                return;
            }
        }
    }

    /** Finale bookkeeping for {@link Descent#beginFinale}. */
    public static boolean finaleActive(ServerLevel level) {
        Zone z = ZONES.get(Strata.COUNT);
        return z != null && z.finale != null && level.getEntity(z.finale) instanceof LivingEntity e && e.isAlive();
    }

    public static void setFinale(ServerLevel level, LivingEntity boss) {
        Zone z = zone(level, Strata.COUNT);
        z.finale = boss.getUUID();
        z.tracked.mobs.add(boss);
    }

    /** Mobs the Descent spawned (counted by the spawner's caps), re-registered when their chunk reloads. */
    public static void track(ServerLevel level, LivingEntity e) {
        int depth = Strata.depthAt(level.dimension(), e.blockPosition());
        if (depth <= 0) return;
        Zone z = zone(level, depth);
        if (!z.tracked.mobs.contains(e)) z.tracked.mobs.add(e);
    }

    // ---- HUD --------------------------------------------------------------------------------------------------------

    private static void hud(ServerPlayer p, Zone z, long now) {
        PState ps = state(p);
        MutableComponent line = Component.literal(Strata.label(z.depth)).withStyle(ChatFormatting.WHITE);
        String status = status(p, z, now);
        if (!status.isEmpty()) line.append(Component.literal("  ·  " + status).withStyle(ChatFormatting.AQUA));
        int noise = z.has(Strata.RULE_LISTENERS) ? Math.round(ps.noise) : -1;
        ps.lastNoiseSent = noise;
        DescentSync.hud(p, z.depth, line, noise, z.rules);
    }

    private static String status(ServerPlayer p, Zone z, long now) {
        if (z.has(Strata.RULE_TIDES)) {
            long c = Math.floorMod(z.age, TIDE_CYCLE);
            return switch (tidePhase(z.age)) {
                case 1 -> "The tide turns in " + secs(TIDE_INVERT - c);
                case 2 -> "Gravity inverted · ebb in " + secs(TIDE_EBB - c);
                case 3 -> "Ebb · ground in " + secs(TIDE_CALM - c);
                default -> "Tide in " + secs(Math.floorMod(TIDE_SWELL - c, TIDE_CYCLE));
            };
        }
        if (z.has(Strata.RULE_FLOOD)) {
            long c = Math.floorMod(z.age, Flood.CYCLE);
            if (c < Flood.RISE) return (c >= Flood.WARN ? "The Choir sings · " : "") + "Water rises in " + secs(Flood.RISE - c);
            if (c < Flood.HOLD) return "The water is rising";
            if (c < Flood.DRAIN) return "High water · drains in " + secs(Flood.DRAIN - c);
            return "Draining";
        }
        if (z.has(Strata.RULE_WIND) && z.age >= z.nextGust - 30 && z.age <= z.gustEnd) return "Gust! Sneak near ledges";
        if (z.has(Strata.RULE_MACHINES) && Foundry.sealed(p.level(), z.depth)) return "The pit is sealed · light the conduits";
        if (z.has(Strata.RULE_SHADOW)) {
            PState ps = state(p);
            if (ps.shadow != null) return "Your shadow hunts you";
        }
        if (z.has(Strata.RULE_RISE)) return "The dead rise once";
        if (z.has(Strata.RULE_ROOMS)) return "Don't look away";
        if (z.has(Strata.RULE_LISTENERS)) return state(p).noise >= HEARD ? "You've been heard" : "Quiet";
        return "";
    }

    private static String secs(long ticks) {
        long s = Math.max(0, ticks / 20);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    /** Zone age (ticks occupied) of a depth, for tests and commands. */
    public static long age(int depth) {
        Zone z = ZONES.get(depth);
        return z == null ? 0 : z.age;
    }

    public static void setAge(ServerLevel level, int depth, long age) {
        zone(level, depth).age = age;
    }
}
