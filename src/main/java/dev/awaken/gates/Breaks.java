package dev.awaken.gates;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.gates.run.Loot;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gate breaks (SPEC section 7): on its break play-day an ignored gate breaks open. A spill wave and a roaming Gate Lord
 * (BREAK role) come out, with a boss bar; the land within 96 blocks goes grey until the Lord dies. Breaks near a Hearth
 * are announced one play-day ahead. A break is an event with better loot, not a punishment.
 */
public final class Breaks {
    private Breaks() {}

    public static final int BLIGHT_RADIUS = 96;
    private static final Map<Integer, ServerBossEvent> BARS = new HashMap<>();
    private static final List<Integer> PENDING = new ArrayList<>();

    static void onNewPlayDay(MinecraftServer server, long day) {
        GateRegistry reg = GateRegistry.get(server);
        for (GateRecord g : List.copyOf(reg.all())) {
            if (g.neverBreaks(server) || !(g.state == GateState.READY || g.state == GateState.OPEN || g.state == GateState.ACTIVE)) continue;
            if (day >= g.breaksOnPlayDay) {
                if (g.state == GateState.ACTIVE && someoneOnlineInside(server, g)) {
                    g.trace.add("break deferred on play-day " + day + ": someone is inside");
                    continue;
                }
                breakGate(server, g);
            } else if (day == g.breaksOnPlayDay - 1 && !g.warned) {
                warn(server, g);
            }
        }
    }

    private static boolean someoneOnlineInside(MinecraftServer server, GateRecord g) {
        for (UUID u : g.inside) if (server.getPlayerList().getPlayer(u) != null) return true;
        return false;
    }

    /** One play-day ahead: tell anyone whose Hearth is within 256 blocks of the gate (and anyone standing near it). */
    static void warn(MinecraftServer server, GateRecord g) {
        ServerLevel over = server.overworld();
        boolean nearHearth = HearthHooks.nearest(over, g.pos, 256).isPresent();
        g.nearHearth |= nearHearth;
        g.warned = true;
        GateRegistry.get(server).setDirty();
        if (!nearHearth) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            boolean home = HearthHooks.hearthOf(p).map(h -> h.distSqr(g.pos) <= 256 * 256).orElse(false);
            boolean close = p.level() == over && p.blockPosition().distSqr(g.pos) <= 512 * 512;
            if (!home && !close) continue;
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("A gate near your Hearth will break tomorrow"))
                    .row("Rank", g.plan.rank().label)
                    .row("Where", (int) Math.sqrt(p.blockPosition().distSqr(g.pos)) + " m " + direction(p.blockPosition(), g.pos))
                    .body(Component.literal("Clear it before then, or be ready to hold the line.")).priority(6).ttl(20 * 10).build());
            DirectorLog.log(p, "gate", "break warning: gate " + g.id + " breaks on play-day " + g.breaksOnPlayDay);
        }
    }

    public static void breakGate(MinecraftServer server, GateRecord g) {
        if (!g.transition(GateState.BROKEN)) g.state = GateState.BROKEN;
        g.trace.add("broke on play-day " + PlayDays.current(server));
        GateRegistry.get(server).setDirty();
        ServerLevel over = server.overworld();
        over.getChunkSource().addTicketWithRadius(GatesRegistry.BREAK_TICKET.get(), ChunkPos.containing(g.pos), 2);
        PENDING.add(g.id);
        over.playSound(null, g.pos, GatesRegistry.SND_BREAK.get(), SoundSource.HOSTILE, 4F, 0.6F);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != over || p.blockPosition().distSqr(g.pos) > 1500.0 * 1500) continue;
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("A gate broke open"))
                    .row("Rank", g.plan.rank().label)
                    .row("Where", (int) Math.sqrt(p.blockPosition().distSqr(g.pos)) + " m " + direction(p.blockPosition(), g.pos))
                    .body(Component.literal("Its Lord walks the land and the colour is draining around it.")).priority(7).ttl(20 * 10).build());
        }
        GatePayloads.syncBeaconsAround(server, g.pos);
    }

    /** Spill wave and Lord once the area is loaded. */
    static void tick(MinecraftServer server) {
        ServerLevel over = server.overworld();
        GateRegistry reg = GateRegistry.get(server);
        if (!PENDING.isEmpty()) {
            PENDING.removeIf(id -> {
                GateRecord g = reg.gate(id);
                if (g == null || g.state != GateState.BROKEN) return true;
                ChunkPos cp = ChunkPos.containing(g.pos);
                if (!over.getChunkSource().hasChunk(cp.x(), cp.z())) return false;
                spill(over, g);
                return true;
            });
        }
        if (server.getTickCount() % 20 != 0) return;
        for (GateRecord g : reg.all()) {
            if (g.state != GateState.BROKEN || g.lord.isEmpty()) continue;
            Entity e = over.getEntity(g.lord.get());
            ServerBossEvent bar = BARS.computeIfAbsent(g.id, k -> {
                ServerBossEvent b = new ServerBossEvent(UUID.randomUUID(), Component.literal(lordName(g)), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
                b.setDarkenScreen(true);
                return b;
            });
            if (e instanceof LivingEntity le && le.isAlive()) {
                bar.setProgress(le.getHealth() / Math.max(1, le.getMaxHealth()));
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    boolean in = p.level() == over && p.distanceToSqr(le) < BLIGHT_RADIUS * BLIGHT_RADIUS;
                    if (in && !bar.getPlayers().contains(p)) bar.addPlayer(p);
                    else if (!in && bar.getPlayers().contains(p)) bar.removePlayer(p);
                }
            } else if (e == null && over.getChunkSource().hasChunk(ChunkPos.containing(g.pos).x(), ChunkPos.containing(g.pos).z())
                    && server.getTickCount() % 600 == 0 && over.getEntitiesOfClass(LivingEntity.class,
                    new net.minecraft.world.phys.AABB(g.pos).inflate(128), x -> x.getUUID().equals(g.lord.get())).isEmpty()) {
                // the Lord vanished without dying (despawned, removed): it comes back to the gate
                g.lord = java.util.Optional.empty();
                spawnLord(over, g);
            }
        }
    }

    static String lordName(GateRecord g) {
        return dev.awaken.gates.run.Rooms.capital(g.plan.theme().bossName) + ", unbound";
    }

    private static void spill(ServerLevel level, GateRecord g) {
        Overworld.place(level, g, true);
        int lv = Progression.contentLevel(g.plan.rank());
        int n = 6 + g.plan.rank().ordinal() * 2;
        BlockPos mouth = g.mouth();
        for (int i = 0; i < n; i++) {
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            int d = 3 + level.getRandom().nextInt(8);
            int x = mouth.getX() + (int) Math.round(Math.cos(a) * d), z = mouth.getZ() + (int) Math.round(Math.sin(a) * d);
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, mouth.getY(), z));
            LivingEntity e = MobHooks.spawnThemed(level, g.plan.theme().themeId(), i == 0 ? MobRole.Role.ELITE : MobRole.Role.NORMAL, at, lv, g.plan.theme().mobTags(), g.key());
            if (e instanceof Mob m) m.setHomeTo(g.pos, 48);
        }
        if (g.lord.isEmpty()) spawnLord(level, g);
        GateRegistry.get(level.getServer()).setDirty();
        GatePayloads.syncBeaconsAround(level.getServer(), g.pos);
    }

    private static void spawnLord(ServerLevel level, GateRecord g) {
        int lv = Progression.contentLevel(g.plan.rank());
        LivingEntity lord = MobHooks.spawnThemed(level, g.plan.theme().themeId(), MobRole.Role.BREAK, g.mouth(), lv, g.plan.theme().mobTags(), g.key());
        if (lord == null) return;
        if (lord.getMaxHealth() < 80) {
            // the fallback spawner gives a plain monster: make it a Lord in size and toughness
            var hp = lord.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.addOrReplacePermanentModifier(new AttributeModifier(dev.awaken.Awaken.id("gate_break_lord"), 7.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            var sc = lord.getAttribute(Attributes.SCALE);
            if (sc != null) sc.addOrReplacePermanentModifier(new AttributeModifier(dev.awaken.Awaken.id("gate_break_lord_scale"), 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            lord.setHealth(lord.getMaxHealth());
        }
        lord.setCustomName(Component.literal(lordName(g)));
        if (lord instanceof Mob m) {
            m.setHomeTo(g.pos, 40);
            m.setPersistenceRequired();
        }
        g.lord = java.util.Optional.of(lord.getUUID());
    }

    /** The roaming Lord fell: colour comes back, loot drops, the gate is gone. */
    static void onLordDeath(ServerLevel level, GateRecord g, LivingEntity lord, ServerPlayer killer) {
        MinecraftServer server = level.getServer();
        ServerBossEvent bar = BARS.remove(g.id);
        if (bar != null) bar.removeAllPlayers();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, lord.getBoundingBox().inflate(BLIGHT_RADIUS))) {
            List<ItemStack> loot = Loot.breakLord(g.plan.rank(), g.plan.theme(), p.getRandom());
            for (ItemStack s : loot) {
                if (p == killer) level.addFreshEntity(new ItemEntity(level, lord.getX(), lord.getY() + 0.5, lord.getZ(), s));
                else if (!p.addItem(s) && !s.isEmpty()) p.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            if (g.nearHearth || HearthHooks.nearest(level, g.pos, 256).isPresent()) Progression.count(p, Counters.BREAKS_HELD_AT_HEARTH);
            Progression.bestMoment(p, "Held a broken " + g.plan.rank().label + "-rank gate (" + g.plan.theme().displayName + ")");
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The break is held"))
                    .body(Component.literal(dev.awaken.core.api.StoryHooks.colourRestored(server) ? "Its Lord is gone." : "The colour comes back.")).row("Lord", lordName(g)).priority(5).ttl(20 * 8).build());
        }
        level.addFreshEntity(new ItemEntity(level, lord.getX(), lord.getY() + 0.5, lord.getZ(), new ItemStack(CoreApi.LORD_HEART.get())));
        g.lord = java.util.Optional.empty();
        g.trace.add("break held" + (killer != null ? " by " + killer.getGameProfile().name() : ""));
        GateService.release(server, g);
        GatePayloads.syncBeaconsAround(server, g.pos);
    }

    static String direction(BlockPos from, BlockPos to) {
        double a = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
        int d = (int) Math.round(((a + 90 + 360) % 360) / 45.0) % 8;
        return Director.DIRS[d];
    }

    /** Day's End: the nearest gate about to break. */
    static String waitingLine(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        long today = PlayDays.current(server);
        GateRecord best = null;
        double bestD = Double.MAX_VALUE;
        for (GateRecord g : GateRegistry.get(server).near(p.blockPosition(), Director.MAX_DIST, true)) {
            if (g.neverBreaks(server) || g.breaksIn(today) > 1) continue;
            double d = g.pos.distSqr(p.blockPosition());
            if (d < bestD) {
                bestD = d;
                best = g;
            }
        }
        if (best == null) return null;
        long in = best.breaksIn(today);
        return "A " + best.plan.rank().label + "-rank gate " + (int) Math.sqrt(bestD) + " m " + direction(p.blockPosition(), best.pos)
                + (in == 0 ? " breaks today" : " breaks tomorrow");
    }

    static void clear() {
        for (ServerBossEvent b : BARS.values()) b.removeAllPlayers();
        BARS.clear();
        PENDING.clear();
    }
}
