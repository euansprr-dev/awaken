package dev.awaken.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.api.TheLull;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.road.Holdfasts;
import dev.awaken.village.world.Plans;
import dev.awaken.village.world.SettlementGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A holdfast's bell (SPEC §43.2, JOURNEY part 1): a single toll on every in-game hour and three at dawn, noon and dusk,
 * heard {@link TheLull#HOLDFAST_BELL} blocks off. It rings only while someone is in reach. The first time a player hears
 * one the System says so and the bell's place is kept (stage 4 shades its bearing on the Map); standing inside its walls
 * is kept too (stage 4 files its bell as Rung). Saved per player, so several players each hear and file it themselves.
 */
public final class Holdfast extends SavedData {
    static final int SCHEMA = 1;
    /** Ticks between a dawn, noon or dusk peal's three tolls. */
    static final int PEAL_GAP = 50;
    /** The nearest a far bell is played from (the client's falloff is short): along its bearing, quieter with distance. */
    static final double NEAR = 32;

    /** What one player knows of one holdfast: its bell's place, and whether they've stood inside its walls. */
    public record Known(long bell, boolean inside) {
        static final Codec<Known> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("bell").forGetter(Known::bell),
                Codec.BOOL.optionalFieldOf("inside", false).forGetter(Known::inside)
        ).apply(i, Known::new));
    }

    static final Codec<Holdfast> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(h -> SCHEMA),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(Codec.STRING, Known.CODEC)).optionalFieldOf("known", Map.of())
                    .forGetter(h -> h.known)
    ).apply(i, (schema, known) -> new Holdfast(known)));

    static final SavedDataType<Holdfast> TYPE = new SavedDataType<>(Awaken.id("village_holdfasts"), Holdfast::new, CODEC);

    final Map<UUID, Map<String, Known>> known = new HashMap<>();

    public Holdfast() {}

    Holdfast(Map<UUID, Map<String, Known>> known) {
        known.forEach((u, m) -> this.known.put(u, new HashMap<>(m)));
    }

    static Holdfast get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The holdfasts a player has heard (key to what they know). */
    public static Map<String, Known> heard(ServerPlayer p) {
        return Map.copyOf(get(p.level().getServer()).known.getOrDefault(p.getUUID(), Map.of()));
    }

    // ---- the bell ---------------------------------------------------------------------------------------------------

    private record Toll(String key, BlockPos bell, long at) {}

    /** The hour each bell last rang (in-game hours since the world began), and the peals' tolls still to come. */
    private static final Map<String, Long> RUNG = new HashMap<>();
    private static final List<Toll> TOLLS = new ArrayList<>();

    static void serverStopped() {
        RUNG.clear();
        TOLLS.clear();
    }

    /** How many tolls an hour's bell rings: three at dawn (0), noon (6000) and dusk (12000), else one. */
    static int tolls(long clock) {
        int hour = (int) (Math.floorMod(clock, 24000L) / 1000);
        return hour == 0 || hour == 6 || hour == 12 ? 3 : 1;
    }

    static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = server.getTickCount();
        for (int k = TOLLS.size() - 1; k >= 0; k--) {
            Toll t = TOLLS.get(k);
            if (t.at > now) continue;
            TOLLS.remove(k);
            toll(level, t.key, t.bell);
        }
        if (now % 20 != 13 || level.players().isEmpty() || !VillageService.enabled(server)) return;
        long clock = level.getOverworldClockTime(), hour = Math.floorDiv(clock, 1000L);
        Map<String, BlockPos> bells = new HashMap<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            for (SettlementGrid.Anchor a : Holdfasts.near(p.getBlockX(), p.getBlockZ(), TheLull.HOLDFAST_BELL, false))
                bells.computeIfAbsent(a.key(), k -> bell(level, a, p));
            VillageService.Live l = VillageService.at(level, p.blockPosition());
            if (l != null && Holdfasts.isKey(l.key())) stoodInside(p, l.key(), bell(level, l.plan()));
        }
        for (var e : bells.entrySet()) {
            Long last = RUNG.put(e.getKey(), hour);
            // first seen this session: it rings only if the hour has only just come (no toll on walking into reach)
            boolean due = last == null ? Math.floorMod(clock, 1000L) < 20 : last != hour;
            if (!due) continue;
            int n = tolls(clock);
            for (int k = 0; k < n; k++) TOLLS.add(new Toll(e.getKey(), e.getValue(), now + 1 + (long) k * PEAL_GAP));
        }
    }

    /** One toll: every player in reach hears it, from its place or, far off, from along its bearing. */
    static void toll(ServerLevel level, String key, BlockPos bell) {
        for (ServerPlayer p : level.players()) {
            double dx = bell.getX() + 0.5 - p.getX(), dy = bell.getY() + 0.5 - p.getEyeY(), dz = bell.getZ() + 0.5 - p.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > TheLull.HOLDFAST_BELL) continue;
            double k = d > NEAR ? NEAR / d : 1;
            float volume = d > NEAR ? (float) (1.0 - 0.7 * (d - NEAR) / (TheLull.HOLDFAST_BELL - NEAR)) : 1.0F;
            p.connection.send(new ClientboundSoundPacket(VillageContent.HOLDFAST_BELL, SoundSource.BLOCKS, p.getX() + dx * k, p.getEyeY() + dy * k,
                    p.getZ() + dz * k, volume, 1.0F, level.getRandom().nextLong()));
            heard(p, key, bell);
        }
    }

    /** A player heard a holdfast's bell: the first time, the System says where from, and the bell's place is kept. */
    static void heard(ServerPlayer p, String key, BlockPos bell) {
        Holdfast st = get(p.level().getServer());
        Map<String, Known> mine = st.known.computeIfAbsent(p.getUUID(), u -> new HashMap<>());
        if (mine.containsKey(key)) return;
        mine.put(key, new Known(bell.asLong(), false));
        st.setDirty();
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE,
                Component.literal("[A bell. A real one, ringing, somewhere " + bearing(bell.getX() - p.getX(), bell.getZ() - p.getZ()) + ".]"))
                .ttl(20 * 8).build());
        Awaken.LOG.info("[village] {} heard the holdfast {}'s bell", p.getName().getString(), key);
    }

    /** A player stands inside a holdfast's walls: kept (stage 4 files its bell). Hearing it is implied. */
    static void stoodInside(ServerPlayer p, String key, BlockPos bell) {
        Holdfast st = get(p.level().getServer());
        Map<String, Known> mine = st.known.computeIfAbsent(p.getUUID(), u -> new HashMap<>());
        Known k = mine.get(key);
        if (k != null && k.inside) return;
        mine.put(key, new Known(k != null ? k.bell : bell.asLong(), true));
        st.setDirty();
    }

    /** "to the north-east": the way from a player to a place (north is -z). */
    static String bearing(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        int k = (int) Math.floorMod(Math.round(a / 45.0), 8L);
        return new String[]{"to the north", "to the north-east", "to the east", "to the south-east", "to the south", "to the south-west",
                "to the west", "to the north-west"}[k];
    }

    /** Where a holdfast's bell hangs: its tower's bell once the town is planned, else high over its middle. */
    private static BlockPos bell(ServerLevel level, SettlementGrid.Anchor a, ServerPlayer near) {
        VillagePlan plan = Plans.cached(a.key());
        if (plan != null) return bell(level, plan);
        int y = level.isLoaded(new BlockPos(a.x(), 0, a.z())) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, a.x(), a.z()) + 16
                : near.getBlockY() + 24;
        return new BlockPos(a.x(), y, a.z());
    }

    static BlockPos bell(ServerLevel level, VillagePlan p) {
        if (p.bell != null) return new BlockPos(p.ox + p.bell.bx(), p.baseY + p.bell.lift() + p.bell.bry(), p.oz + p.bell.bz());
        return new BlockPos(p.ox, p.baseY + 16, p.oz);
    }
}
