package dev.awaken.hearth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.hearth.echo.EchoRole;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** One player's Hearth. Saved in {@link HearthData}; rooms and other caches are rebuilt at runtime. */
public final class HearthRecord {
    public static final int MAX_TIER = 5;
    public static final int MAX_FORGE_TIER = 3;

    public final UUID owner;
    public String ownerName;
    public BlockPos pos;
    public String dimension;
    public int tier = 1;
    public int forgeTier = 1;
    /** Town NPC ids that have arrived, and the entity each one lives in. */
    public final Set<String> arrived = new LinkedHashSet<>();
    public final Map<String, UUID> npcs = new HashMap<>();
    public BlockPos spire;
    /** Role pads inside the radius, in placement order (assignment order). */
    public final LinkedHashMap<BlockPos, EchoRole> pads = new LinkedHashMap<>();
    /** Echo entity currently working each pad (runtime + saved so we don't double-spawn after a restart). */
    public final Map<BlockPos, UUID> padEchoes = new HashMap<>();
    /** Gate breaks seen near this Hearth, by gate id. */
    public final Map<String, BreakState> breaks = new HashMap<>();
    /** Rank ordinal of a rank-up still waiting to be celebrated at home (-1 = none). */
    public int pendingCelebration = -1;
    /** Ticks of Mine duty accumulated since the last ore delivery, per pad. */
    public final Map<BlockPos, Integer> mineProgress = new HashMap<>();
    public long createdDay;
    public long itemsHauled, cropsHarvested, oresBrought;

    // ---- runtime only -----------------------------------------------------------------------------------------------
    public volatile List<Room> rooms = List.of();
    /** Game time after which a rescan should run (0 = not scheduled). */
    public long scanAt = 1;
    public long lastScan;
    /** Distinct boss trophies hung inside Trophy Halls (from the last scan). */
    public Set<String> hallTrophies = Set.of();
    /** Rooms aren't announced on the first scan after a server start (they were already known), only after placing. */
    public boolean scannedOnce;
    public boolean announceFirstScan;

    public record BreakState(String gateId, long counterAtBreak, boolean counted, BlockPos gatePos) {
        public static final Codec<BreakState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("gate").forGetter(BreakState::gateId),
                Codec.LONG.fieldOf("counter").forGetter(BreakState::counterAtBreak),
                Codec.BOOL.fieldOf("counted").forGetter(BreakState::counted),
                BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(BreakState::gatePos)
        ).apply(i, BreakState::new));
    }

    private record Pad(BlockPos pos, EchoRole role, Optional<UUID> echo, int mine) {
        static final Codec<Pad> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Pad::pos),
                EchoRole.CODEC.fieldOf("role").forGetter(Pad::role),
                UUIDUtil.CODEC.optionalFieldOf("echo").forGetter(Pad::echo),
                Codec.INT.optionalFieldOf("mine", 0).forGetter(Pad::mine)
        ).apply(i, Pad::new));
    }

    private record Npc(String id, Optional<UUID> entity) {
        static final Codec<Npc> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Npc::id),
                UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(Npc::entity)
        ).apply(i, Npc::new));
    }

    private record Core(UUID owner, String ownerName, BlockPos pos, String dimension, int tier, int forgeTier, long createdDay,
                        int pendingCelebration, Optional<BlockPos> spire) {
        static final Codec<Core> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Core::owner),
                Codec.STRING.fieldOf("name").forGetter(Core::ownerName),
                BlockPos.CODEC.fieldOf("pos").forGetter(Core::pos),
                Codec.STRING.optionalFieldOf("dimension", "minecraft:overworld").forGetter(Core::dimension),
                Codec.INT.fieldOf("tier").forGetter(Core::tier),
                Codec.INT.optionalFieldOf("forge_tier", 1).forGetter(Core::forgeTier),
                Codec.LONG.optionalFieldOf("created_day", 0L).forGetter(Core::createdDay),
                Codec.INT.optionalFieldOf("pending_celebration", -1).forGetter(Core::pendingCelebration),
                BlockPos.CODEC.optionalFieldOf("spire").forGetter(Core::spire)
        ).apply(i, Core::new));
    }

    private record Town(List<Npc> npcs, List<Pad> pads, List<BreakState> breaks, List<Long> tallies) {
        static final Codec<Town> CODEC = RecordCodecBuilder.create(i -> i.group(
                Npc.CODEC.listOf().optionalFieldOf("npcs", List.of()).forGetter(Town::npcs),
                Pad.CODEC.listOf().optionalFieldOf("pads", List.of()).forGetter(Town::pads),
                BreakState.CODEC.listOf().optionalFieldOf("breaks", List.of()).forGetter(Town::breaks),
                Codec.LONG.listOf().optionalFieldOf("tallies", List.of()).forGetter(Town::tallies)
        ).apply(i, Town::new));
    }

    public static final Codec<HearthRecord> CODEC = RecordCodecBuilder.<HearthRecord>create(i -> i.group(
            Core.CODEC.fieldOf("core").forGetter(HearthRecord::core),
            Town.CODEC.fieldOf("town").forGetter(HearthRecord::town)
    ).apply(i, HearthRecord::fromParts));

    public HearthRecord(UUID owner, String ownerName, BlockPos pos, String dimension) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.pos = pos;
        this.dimension = dimension;
    }

    private Core core() {
        return new Core(owner, ownerName, pos, dimension, tier, forgeTier, createdDay, pendingCelebration, Optional.ofNullable(spire));
    }

    private Town town() {
        List<Npc> n = new ArrayList<>();
        for (String id : arrived) n.add(new Npc(id, Optional.ofNullable(npcs.get(id))));
        List<Pad> p = new ArrayList<>();
        pads.forEach((pos, role) -> p.add(new Pad(pos, role, Optional.ofNullable(padEchoes.get(pos)), mineProgress.getOrDefault(pos, 0))));
        return new Town(n, p, List.copyOf(breaks.values()), List.of(itemsHauled, cropsHarvested, oresBrought));
    }

    private static HearthRecord fromParts(Core c, Town t) {
        HearthRecord r = new HearthRecord(c.owner, c.ownerName, c.pos, c.dimension);
        r.tier = Math.max(1, Math.min(MAX_TIER, c.tier));
        r.forgeTier = Math.max(1, Math.min(MAX_FORGE_TIER, c.forgeTier));
        r.createdDay = c.createdDay;
        r.pendingCelebration = c.pendingCelebration;
        r.spire = c.spire.orElse(null);
        for (Npc n : t.npcs) {
            r.arrived.add(n.id);
            n.entity.ifPresent(u -> r.npcs.put(n.id, u));
        }
        for (Pad p : t.pads) {
            r.pads.put(p.pos, p.role);
            p.echo.ifPresent(u -> r.padEchoes.put(p.pos, u));
            if (p.mine > 0) r.mineProgress.put(p.pos, p.mine);
        }
        for (BreakState b : t.breaks) r.breaks.put(b.gateId(), b);
        if (t.tallies.size() >= 3) {
            r.itemsHauled = t.tallies.get(0);
            r.cropsHarvested = t.tallies.get(1);
            r.oresBrought = t.tallies.get(2);
        }
        return r;
    }

    // ---- queries ----------------------------------------------------------------------------------------------------

    public int radius() {
        return radiusForTier(tier);
    }

    public static int radiusForTier(int tier) {
        return 48 + 8 * (Math.max(1, Math.min(MAX_TIER, tier)) - 1);
    }

    public boolean contains(BlockPos p) {
        int r = radius();
        return Math.abs(p.getX() - pos.getX()) <= r && Math.abs(p.getZ() - pos.getZ()) <= r
                && p.getY() >= pos.getY() - 32 && p.getY() <= pos.getY() + 64;
    }

    public AABB box() {
        int r = radius();
        return new AABB(pos.getX() - r, pos.getY() - 32, pos.getZ() - r, pos.getX() + r + 1, pos.getY() + 65, pos.getZ() + r + 1);
    }

    public boolean hasRoom(RoomType type) {
        for (Room room : rooms) if (room.types().contains(type)) return true;
        return false;
    }

    public Optional<Room> roomAt(BlockPos p, RoomType type) {
        for (Room room : rooms) if (room.types().contains(type) && room.contains(p)) return Optional.of(room);
        return Optional.empty();
    }

    public void scheduleScan(long gameTime, int delay) {
        long at = gameTime + delay;
        if (scanAt == 0 || scanAt > at) scanAt = at;
    }
}
