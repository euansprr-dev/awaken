package dev.awaken.delve.cave;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.Notices;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.SeepHearts;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Finds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cave places at run time (SPEC §28.5): which place each player is in (sent to their client for the senses and its
 * caption, the first time each play-day; the first time ever is a discovery), a seep's rule (creatures in it +5 over the
 * region, its heart rooted at the socket while a player is near, its Hollows nest, all of it falling quiet when the heart
 * dies), a sinkhole's bats at dusk, a grotto's cache counted as a region secret, and the places near you for their look
 * from outside (a sinkhole's mist, a chute's draught). Places become known as their start chunks load.
 */
public final class CavePlaces {
    private CavePlaces() {}

    /** How strongly a live seep presses at its heart and fades toward its edge (blocks). */
    static final double SEEP_FULL = 15, SEEP_FADE = 9;
    public static final int SEEP_LEVELS = 5;
    static final int NEST_MOBS = 3, NEST_EVERY = 600, HEART_REACH = 40, NEAR = 160, GROTTO_NEAR = 64;

    /** Places whose start has loaded, per id (every dimension's are overworld's: places only generate there). */
    static final Map<Long, Spec> KNOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();
    private static final Map<Long, Long> NEST_AT = new HashMap<>();
    private static final Map<Long, Long> BATS_DAY = new HashMap<>();

    private static final class Tracked {
        long place = Long.MIN_VALUE;
        float seep = -1;
        boolean quiet;
        final Map<Long, Long> captioned = new HashMap<>();
        List<Long> near = List.of();
    }

    static void register() {
        dev.awaken.living.api.Regions.addLevelExtra(CavePlaces::levelExtra);
    }

    // ---- what's where ----------------------------------------------------------------------------------------------

    static void onChunkLoad(ChunkEvent.Load e) {
        if (!(e.getChunk() instanceof LevelChunk lc) || !(lc.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        for (var entry : lc.getAllStarts().entrySet()) {
            if (!(entry.getKey() instanceof CaveStructure)) continue;
            Spec s = spec(entry.getValue());
            if (s != null) KNOWN.put(s.id(), s);
        }
    }

    static @Nullable Spec spec(StructureStart start) {
        if (start == null || !start.isValid()) return null;
        for (StructurePiece p : start.getPieces()) if (p instanceof CavePiece cp) return cp.spec();
        return null;
    }

    /** Places built by a command or a test (no structure start of their own), newest first. */
    static final List<Spec> STAGED = new java.util.concurrent.CopyOnWriteArrayList<>();

    static void stage(Spec s) {
        STAGED.removeIf(o -> o.box().intersects(s.box()));
        STAGED.addFirst(s);
        KNOWN.put(s.id(), s);
    }

    /** The cave place whose box holds {@code pos}, or null. */
    public static @Nullable Spec at(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD || !level.hasChunkAt(pos)) return null;
        for (Spec s : STAGED) if (s.box().isInside(pos)) return s;
        StructureStart st = level.structureManager().getStructureWithPieceAt(pos, h -> h.value() instanceof CaveStructure);
        Spec s = spec(st);
        if (s != null) KNOWN.putIfAbsent(s.id(), s);
        return s;
    }

    /** How strongly a live seep presses at {@code pos} (0..1): full in its hall and heart chamber, fading out past them. */
    static float seepAt(Spec s, double x, double y, double z) {
        if (s.kind() != CaveKind.SEEP) return 0F;
        int[] c = Seep.chamber(s);
        double dh = Math.sqrt(sq(x - s.x() - 0.5) + sq((y - s.y() - 4) * 1.6) + sq(z - s.z() - 0.5));
        double dc = Math.sqrt(sq(x - c[0] - 0.5) + sq((y - c[1] - 3) * 1.6) + sq(z - c[2] - 0.5));
        double d = Math.min(dh, dc + 6);
        return (float) Math.max(0, Math.min(1, 1 - (d - SEEP_FULL) / SEEP_FADE));
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Living's Regions asks: a live seep is five levels harder than its region. */
    static int levelExtra(ServerLevel level, BlockPos pos) {
        Spec s = at(level, pos);
        if (s == null || s.kind() != CaveKind.SEEP || CaveData.get(level).quiet(s.id())) return 0;
        return seepAt(s, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 0.3F ? SEEP_LEVELS : 0;
    }

    // ---- each player, twice a second --------------------------------------------------------------------------------

    static void tick(ServerPlayer p) {
        if ((p.tickCount + p.getId()) % 10 != 0 || !(p.level() instanceof ServerLevel level)) return;
        Tracked t = TRACKED.computeIfAbsent(p.getUUID(), k -> new Tracked());
        Spec s = level.dimension() == Level.OVERWORLD ? at(level, p.blockPosition()) : null;
        long id = s == null ? Long.MIN_VALUE : s.id();
        boolean quiet = s != null && s.kind() == CaveKind.SEEP && CaveData.get(level).quiet(id);
        float seep = s == null || quiet ? 0F : seepAt(s, p.getX(), p.getY(), p.getZ());
        if (id != t.place || quiet != t.quiet || Math.abs(seep - t.seep) > 0.08F) {
            boolean caption = false;
            if (s != null && id != t.place) caption = arrived(p, s, t);
            t.place = id;
            t.quiet = quiet;
            t.seep = seep;
            AwakenNet.send(p, new CaveNet.Now(s == null ? -1 : s.kind().ordinal(), s == null ? "" : CaveNames.name(s), seep, quiet, caption));
        }
        if (s != null) live(level, p, s, quiet);
        if ((p.tickCount + p.getId()) % 40 == 0) near(level, p, t);
    }

    /** Arriving in a place: a discovery the first time, a caption the first time each play-day. */
    private static boolean arrived(ServerPlayer p, Spec s, Tracked t) {
        String key = "cave/" + s.kind().id + "/" + s.id();
        if (Progression.discover(p, key, s.kind().big ? 0.15 : 0.08)) {
            Progression.count(p, "awaken:cave_places_found");
            Progression.markNewThing(p, "cave/" + s.kind().id);
        }
        long day = PlayDays.current(p.level().getServer());
        Long last = t.captioned.get(s.id());
        if (last != null && last == day) return false;
        t.captioned.put(s.id(), day);
        return true;
    }

    /** A place that has someone in it: a seep's heart and nest, a sinkhole's bats. */
    static void live(ServerLevel level, ServerPlayer p, Spec s, boolean quiet) {
        switch (s.kind()) {
            case SEEP -> {
                if (quiet || p.isSpectator()) return;
                int[] c = Seep.chamber(s);
                BlockPos socket = new BlockPos(c[0], c[1], c[2]);
                if (p.blockPosition().distSqr(socket) < HEART_REACH * HEART_REACH && (p.tickCount & 31) < 10) SeepHearts.spawn(level, socket);
                nest(level, p, s);
            }
            case SINKHOLE -> bats(level, s);
            default -> {
            }
        }
    }

    /** The Hollows' nest in a live seep: up to three of them about it while someone is near, one more every 30 s. */
    private static void nest(ServerLevel level, ServerPlayer p, Spec s) {
        int[] c = Seep.chamber(s);
        double ux = c[0] - s.x(), uz = c[2] - s.z(), ul = Math.max(1e-3, Math.sqrt(ux * ux + uz * uz));
        // the nest's spot, as the builder drew it (its side drawn from the seed the same way)
        double side = Carve.rand(s.seed() + 30, 0, 0, 0) < 0.5 ? 1 : -1;
        int nx = (int) Math.floor(s.x() + 0.5 + ux / ul * 2 - uz / ul * side * (Seep.HALL_WIDE + 3.5));
        int nz = (int) Math.floor(s.z() + 0.5 + uz / ul * 2 + ux / ul * side * (Seep.HALL_WIDE + 3.5));
        BlockPos nest = new BlockPos(nx, s.y(), nz);
        if (p.blockPosition().distSqr(nest) > 28 * 28) return;
        long now = level.getGameTime();
        Long last = NEST_AT.get(s.id());
        if (last != null && now - last < NEST_EVERY) return;
        AABB area = new AABB(nest).inflate(16, 6, 16);
        int alive = level.getEntitiesOfClass(Mob.class, area, m -> m.isAlive() && isHollow(m.getType())).size();
        if (alive >= NEST_MOBS) return;
        NEST_AT.put(s.id(), now);
        EntityType<? extends Mob> type = MobTypes.type(level.getRandom().nextInt(3) == 0 ? "drip_lurker" : "hushling");
        if (type == null) return;
        Mob m = type.create(level, EntitySpawnReason.EVENT);
        if (m == null) return;
        m.snapTo(nx + 0.5 + level.getRandom().nextInt(3) - 1, s.y(), nz + 0.5 + level.getRandom().nextInt(3) - 1, level.getRandom().nextFloat() * 360F, 0F);
        if (!level.noCollision(m)) return;
        EventHooks.finalizeMobSpawn(m, level, level.getCurrentDifficultyAt(nest), EntitySpawnReason.EVENT, null);
        level.addFreshEntity(m);
    }

    private static boolean isHollow(EntityType<?> t) {
        return t == MobTypes.type("hushling") || t == MobTypes.type("drip_lurker");
    }

    /** At dusk the bats come up out of a sinkhole, once a play-day. */
    private static void bats(ServerLevel level, Spec s) {
        long time = level.getOverworldClockTime() % 24000L;
        if (time < 12400 || time > 13200) return;
        long day = PlayDays.current(level.getServer());
        Long last = BATS_DAY.get(s.id());
        if (last != null && last == day) return;
        BATS_DAY.put(s.id(), day);
        int n = 7 + level.getRandom().nextInt(6);
        int mid = (s.y() + s.p(Sinkhole.RIM)) / 2;
        for (int i = 0; i < n; i++) {
            var bat = EntityTypes.BAT.create(level, EntitySpawnReason.EVENT);
            if (bat == null) continue;
            double a = level.getRandom().nextDouble() * Math.PI * 2, r = level.getRandom().nextDouble() * s.p(Sinkhole.R) * 0.6;
            bat.snapTo(s.x() + 0.5 + Math.cos(a) * r, mid + level.getRandom().nextInt(12) - 6, s.z() + 0.5 + Math.sin(a) * r, 0F, 0F);
            if (level.noCollision(bat)) level.addFreshEntity(bat);
        }
    }

    /** The places near a player for their look from outside: sinkholes (mist) and chutes (the draught at the mouth). */
    private static void near(ServerLevel level, ServerPlayer p, Tracked t) {
        List<Long> ids = new ArrayList<>();
        List<CaveNet.Site> sites = new ArrayList<>();
        for (Spec s : KNOWN.values()) {
            if (s.kind() != CaveKind.SINKHOLE && s.kind() != CaveKind.CHUTE && s.kind() != CaveKind.GROTTO) continue;
            double dx = s.x() - p.getX(), dz = s.z() - p.getZ();
            if (dx * dx + dz * dz > (s.kind() == CaveKind.GROTTO ? GROTTO_NEAR * GROTTO_NEAR : NEAR * NEAR)) continue;
            ids.add(s.id());
            if (s.kind() == CaveKind.SINKHOLE) sites.add(new CaveNet.Site(s.kind().ordinal(), new BlockPos(s.x(), s.p(Sinkhole.RIM), s.z()), s.p(Sinkhole.R), s.p(Sinkhole.RIM) - s.y()));
            else if (s.kind() == CaveKind.CHUTE) sites.add(new CaveNet.Site(s.kind().ordinal(), new BlockPos(s.x(), s.y(), s.z()), 2, s.rot()));
            else {
                // a grotto's own light: its curtain (depth 0) and its pool (depth 1); radius carries its turn
                int[] c = Grotto.curtain(s), q = Grotto.pool(s);
                sites.add(new CaveNet.Site(s.kind().ordinal(), new BlockPos(c[0], c[1], c[2]), s.rot(), 0));
                sites.add(new CaveNet.Site(s.kind().ordinal(), new BlockPos(q[0], q[1], q[2]), s.rot(), 1));
            }
            if (sites.size() >= 16) break;
        }
        if (ids.equals(t.near)) return;
        t.near = ids;
        AwakenNet.send(p, new CaveNet.Near(sites));
    }

    // ---- events ----------------------------------------------------------------------------------------------------

    /** A seep's heart died: the seep falls quiet for everyone (its hum, its violet, its nest and its +5 go). */
    static void onDeath(LivingDeathEvent e) {
        if (!SeepHearts.is(e.getEntity()) || !(e.getEntity().level() instanceof ServerLevel level)) return;
        Spec s = at(level, e.getEntity().blockPosition());
        if (s == null || s.kind() != CaveKind.SEEP || !CaveData.get(level).quieten(s.id())) return;
        for (ServerPlayer p : level.players()) {
            Spec here = at(level, p.blockPosition());
            if (here == null || here.id() != s.id()) continue;
            Progression.count(p, "awaken:seeps_quieted");
            dev.awaken.signal.Signal.toast(p, Component.literal(CaveNames.name(s) + " falls quiet").withColor(0xC8B4FF), Notices.Tier.IMPORTANT, "cave");
        }
        dev.awaken.Awaken.LOG.info("[delve] the seep at {} {} {} fell quiet", s.x(), s.y(), s.z());
    }

    /** Opening a grotto's buried cache: a region secret, the first time. */
    static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) return;
        if (!(level.getBlockState(e.getPos()).getBlock() instanceof ChestBlock)) return;
        Spec s = at(level, e.getPos());
        if (s == null || s.kind() != CaveKind.GROTTO) return;
        int[] c = Grotto.cache(s);
        if (c[0] != e.getPos().getX() || c[1] != e.getPos().getY() || c[2] != e.getPos().getZ()) return;
        if (!Progression.discover(p, "secret/grotto/" + s.id(), 0.12)) return;
        Progression.count(p, Counters.SECRETS_FOUND);
        Finds.tell(p, Finds.Find.of("secret", "Secret · " + CaveNames.name(s), CaveNames.name(s), "A cache buried in a grotto", 0x9FE6D6, true));
    }

    /**
     * A grotto's pool that is a legendary fishing spot (some are: {@link Grotto#fishingSpot}): a third of its catches bring
     * something more up out of the dark water, and the first catch there is a find.
     */
    static void onFished(net.neoforged.neoforge.event.entity.player.ItemFishedEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) return;
        BlockPos at = e.getHookEntity().blockPosition();
        Spec s = at(level, at);
        if (s == null || s.kind() != CaveKind.GROTTO || !Grotto.fishingSpot(s)) return;
        if (Progression.discover(p, "secret/fishing/" + s.id(), 0.1)) {
            Progression.count(p, Counters.SECRETS_FOUND);
            Finds.tell(p, Finds.Find.of("secret", "Fishing spot · " + CaveNames.name(s), CaveNames.name(s), "The glowing pool bites", 0x9FE6D6, true));
        }
        var r = p.getRandom();
        if (r.nextFloat() > 0.35F) return;
        net.minecraft.world.item.ItemStack extra = switch (r.nextInt(6)) {
            case 0 -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLOW_INK_SAC, 1 + r.nextInt(2));
            case 1 -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 1 + r.nextInt(3));
            case 2 -> new net.minecraft.world.item.ItemStack(dev.awaken.delve.DelveContent.DEEPGLASS_SHARD.get());
            case 3 -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI, 2 + r.nextInt(3));
            case 4 -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PRISMARINE_CRYSTALS, 1 + r.nextInt(2));
            default -> new net.minecraft.world.item.ItemStack(r.nextInt(4) == 0 ? net.minecraft.world.item.Items.NAUTILUS_SHELL : net.minecraft.world.item.Items.GLOW_BERRIES);
        };
        p.getInventory().placeItemBackInInventory(extra, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    static void forget(UUID player) {
        TRACKED.remove(player);
    }

    static void clear() {
        KNOWN.clear();
        STAGED.clear();
        TRACKED.clear();
        NEST_AT.clear();
        BATS_DAY.clear();
    }
}
