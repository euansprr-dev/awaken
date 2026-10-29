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
    /**
     * A gate's break clock starts the first time any player (not a spectator) comes within this many blocks of it: about
     * where the vortex reads clearly over the land (SPEC §7, "Seen first").
     */
    public static final int SEEN_RADIUS = 64;
    /** An unseen gate folds away quietly after this many play-days, so gates nobody visits never pile up. */
    public static final int UNSEEN_DAYS = 7;
    private static final Map<Integer, ServerBossEvent> BARS = new HashMap<>();
    private static final List<Integer> PENDING = new ArrayList<>();

    static void onNewPlayDay(MinecraftServer server, long day) {
        GateRegistry reg = GateRegistry.get(server);
        for (GateRecord g : List.copyOf(reg.all())) newPlayDay(server, g, day);
    }

    /** One gate on a new play-day: warned the day before, broken (or folded, or deferred) on the day. */
    static void newPlayDay(MinecraftServer server, GateRecord g, long day) {
        if (g.neverBreaks(server) || !(g.state == GateState.READY || g.state == GateState.OPEN || g.state == GateState.ACTIVE)) return;
        if (g.waiting(server)) {
            // nobody has come close: no clock, no break; left unseen long enough, it folds away without a sound
            if (g.foldsOnPlayDay >= 0 && day >= g.foldsOnPlayDay && g.inside.isEmpty()) {
                g.trace.add("folded unseen on play-day " + day);
                GateService.release(server, g);
            }
            return;
        }
        if (day >= g.breaksOnPlayDay) {
            if (g.state == GateState.ACTIVE && someoneOnlineInside(server, g)) {
                g.trace.add("break deferred on play-day " + day + ": someone is inside");
                return;
            }
            if (foreignHearthNear(server, g)) {
                // a gate rolled for someone else that stands near a Hearth its owner doesn't share folds away
                // instead of loosing its Lord on that home (a Hearth placed after the gate formed)
                g.trace.add("folded on play-day " + day + ": a Hearth its owner doesn't share is within " + Director.FOREIGN_HEARTH_RADIUS);
                GateService.release(server, g);
                return;
            }
            breakGate(server, g);
        } else if (day == g.breaksOnPlayDay - 1 && !g.warned) {
            warn(server, g);
        }
    }

    // ---- seen first ---------------------------------------------------------------------------------------------------

    /** Once a second: any player who comes within {@link #SEEN_RADIUS} of a waiting gate starts its clock. */
    static void sightings(MinecraftServer server) {
        if (server.getTickCount() % 20 != 7) return;
        // GameTests start their gates' clocks themselves: a test's player never starts another test's
        if (server instanceof net.minecraft.gametest.framework.GameTestServer) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sight(server, p);
    }

    /** The waiting gates this player is close enough to see start their clocks. Returns how many. */
    static int sight(MinecraftServer server, ServerPlayer p) {
        if (p.isSpectator() || p.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return 0;
        int n = 0;
        for (GateRecord g : GateRegistry.get(server).near(p.blockPosition(), SEEN_RADIUS, true)) {
            if (g.state == GateState.BUILDING || !GateExterior.shown(g) || !g.waiting(server)) continue;
            seen(server, g, p.getName().getString());
            n++;
        }
        return n;
    }

    /** Starts a gate's clock: it breaks as many play-days from now as it was rolled with (stabilising counts). */
    static void seen(MinecraftServer server, GateRecord g, String by) {
        long today = PlayDays.current(server);
        long span = Math.max(1, g.breaksOnPlayDay - g.createdDay);
        g.seenDay = today;
        g.breaksOnPlayDay = today + span;
        g.foldsOnPlayDay = -1;
        g.warned = false;
        g.trace.add("seen on play-day " + today + " by " + by + ": breaks on play-day " + g.breaksOnPlayDay);
        GateRegistry.get(server).setDirty();
        GatePayloads.syncBeaconsAround(server, g.pos);
    }

    /** Schema 4 (old saves): every standing gate that hasn't broken waits to be seen again, from today. */
    static void unseeAll(MinecraftServer server, GateRegistry reg) {
        long today = PlayDays.current(server);
        int n = 0;
        for (GateRecord g : reg.all()) if (unsee(g, today)) n++;
        reg.migrated();
        dev.awaken.Awaken.LOG.info("[gates] schema 4: {} standing gates wait to be seen", n);
    }

    /** One gate of an old save: standing and able to break, it waits to be seen; anything else stays as it was. */
    static boolean unsee(GateRecord g, long today) {
        if (!g.state.standing() || g.scripted || g.wandering() || g.plan.skybornTrial()) return false;
        g.seenDay = -1;
        g.foldsOnPlayDay = today + UNSEEN_DAYS;
        g.warned = false;
        g.trace.add("waits to be seen (gates schema 4, play-day " + today + ")");
        return true;
    }

    /** Whether a Hearth the gate's owner doesn't share stands within a break's reach (gates with no owner: never). */
    static boolean foreignHearthNear(MinecraftServer server, GateRecord g) {
        if (g.owner.isEmpty()) return false;
        ServerLevel over = server.overworld();
        for (BlockPos anchor : HearthHooks.within(over, g.pos, Director.FOREIGN_HEARTH_RADIUS)) {
            if (!HearthHooks.installed().belongs(over, g.owner.get(), anchor)) return true;
        }
        return false;
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
            boolean home = HearthHooks.hearthsOf(p).stream().anyMatch(h -> h.distSqr(g.pos) <= 256 * 256);
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
        g.spillPending = true;
        PENDING.add(g.id);
        over.playSound(null, g.pos, GatesRegistry.SND_BREAK.get(), SoundSource.HOSTILE, 4F, 0.6F);
        dev.awaken.core.audio.AudioCues.playNear(over, g.pos, 640, dev.awaken.core.audio.Cue.GATE_BROKE);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != over || p.blockPosition().distSqr(g.pos) > 1500.0 * 1500) continue;
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("A gate broke open"))
                    .row("Rank", g.plan.rank().label)
                    .row("Where", (int) Math.sqrt(p.blockPosition().distSqr(g.pos)) + " m " + direction(p.blockPosition(), g.pos))
                    .body(Component.literal("Its Lord walks the land and the colour is draining around it.")).priority(7).ttl(20 * 10).build());
        }
        GatePayloads.syncBeaconsAround(server, g.pos);
    }

    /** Who comes close (once a second), then the spill wave and Lord once the area is loaded. */
    static void tick(MinecraftServer server) {
        sightings(server);
        ServerLevel over = server.overworld();
        GateRegistry reg = GateRegistry.get(server);
        if (!PENDING.isEmpty()) {
            PENDING.removeIf(id -> {
                GateRecord g = reg.gate(id);
                if (g == null || g.state != GateState.BROKEN) return true;
                ChunkPos cp = ChunkPos.containing(g.pos);
                if (!over.getChunkSource().hasChunk(cp.x(), cp.z())) return false;
                spill(over, g);
                g.spillPending = false;
                return true;
            });
        }
        if (server.getTickCount() % 20 != 0) return;
        for (GateRecord g : List.copyOf(reg.all())) {
            if (g.state != GateState.BROKEN) continue;
            if (g.breakHeld) {
                // the Lord fell but the release was refused (someone stood in the cell): try again
                if (server.getTickCount() % 100 == 0) GateService.release(server, g);
                continue;
            }
            if (g.spillPending) {
                // a restart dropped the queued spill: queue it again
                if (!PENDING.contains(g.id)) {
                    over.getChunkSource().addTicketWithRadius(GatesRegistry.BREAK_TICKET.get(), ChunkPos.containing(g.pos), 2);
                    PENDING.add(g.id);
                }
                continue;
            }
            if (g.lord.isEmpty()) {
                // no Lord on record (it couldn't take form): bring one out once its home area is loaded
                if (server.getTickCount() % 200 == 0 && areaLoaded(over, g.pos)) spawnLord(over, g);
                continue;
            }
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
                MISSES.remove(g.id);
            } else if (e == null && server.getTickCount() % 200 == 0 && areaLoaded(over, g.pos)) {
                // the Lord vanished without dying (removed). Entity sections load after chunks, so only after three
                // checks in a row with its whole home area loaded; a stray copy that loads later is discarded on join.
                if (MISSES.merge(g.id, 1, Integer::sum) >= 3) {
                    MISSES.remove(g.id);
                    g.lord = java.util.Optional.empty();
                    spawnLord(over, g);
                }
            } else if (e != null) {
                MISSES.remove(g.id);
            }
        }
    }

    private static final Map<Integer, Integer> MISSES = new HashMap<>();

    /** The Lord's home area (it roams within 40 blocks of the gate) is loaded and ticking entities. */
    private static boolean areaLoaded(ServerLevel level, BlockPos pos) {
        for (int dx = -48; dx <= 48; dx += 48) {
            for (int dz = -48; dz <= 48; dz += 48) {
                if (!level.isPositionEntityTicking(pos.offset(dx, 0, dz))) return false;
            }
        }
        return true;
    }

    /**
     * A break Lord loaded from disk that its gate no longer claims (a duplicate, or its gate is gone): discard it so a
     * broken gate never has two Lords and a released gate leaves no unkillable leftovers.
     */
    static boolean strayLord(MinecraftServer server, Entity e) {
        MobRole role = MobRole.of(e);
        if (role.role() != MobRole.Role.BREAK || role.gateId().isEmpty()) return false;
        GateRecord g;
        try {
            g = GateRegistry.get(server).gate(Integer.parseInt(role.gateId()));
        } catch (NumberFormatException ex) {
            return false;
        }
        return g == null || g.state != GateState.BROKEN || g.breakHeld || (g.lord.isPresent() && !g.lord.get().equals(e.getUUID()));
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
        g.breakHeld = true;
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
            if (!g.counting(server) || g.breaksIn(today) > 1) continue;
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
        MISSES.clear();
    }
}
