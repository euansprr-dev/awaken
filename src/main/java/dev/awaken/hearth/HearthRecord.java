package dev.awaken.hearth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.hearth.echo.EchoRole;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.Miss;
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
    /** A cell inside the cottage Oren raised here (null = none): the guide says "Oren built this one". */
    public BlockPos cottage;
    /** Players who share this Hearth with its owner (SPEC "Together"), uuid → last known name, in the order they joined. */
    public final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
    /** The owner of the Hearth this one was founded from as a sister home (null = founded on its own). */
    public UUID foundedFrom;
    /** Members' rank-ups still waiting to be celebrated here: uuid → rank ordinal. */
    public final Map<UUID, Integer> memberCelebrations = new HashMap<>();

    // ---- runtime only -----------------------------------------------------------------------------------------------
    public volatile List<Room> rooms = List.of();
    /** Furniture in the radius that isn't in a room, and why (from the last scan). */
    public List<Miss> misses = List.of();
    /** How many of each {@link Furniture} the radius holds (from the last scan). */
    public int[] furniture = new int[Furniture.COUNT];
    /** Game time the Anchor was placed this session (0 = before this server start): the guide's first nudge. */
    public long placedAt;
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
                        int pendingCelebration, Optional<BlockPos> spire, Optional<BlockPos> cottage) {
        static final Codec<Core> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Core::owner),
                Codec.STRING.fieldOf("name").forGetter(Core::ownerName),
                BlockPos.CODEC.fieldOf("pos").forGetter(Core::pos),
                Codec.STRING.optionalFieldOf("dimension", "minecraft:overworld").forGetter(Core::dimension),
                Codec.INT.fieldOf("tier").forGetter(Core::tier),
                Codec.INT.optionalFieldOf("forge_tier", 1).forGetter(Core::forgeTier),
                Codec.LONG.optionalFieldOf("created_day", 0L).forGetter(Core::createdDay),
                Codec.INT.optionalFieldOf("pending_celebration", -1).forGetter(Core::pendingCelebration),
                BlockPos.CODEC.optionalFieldOf("spire").forGetter(Core::spire),
                BlockPos.CODEC.optionalFieldOf("cottage").forGetter(Core::cottage)
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

    /** One member: who, and the name they had when last seen (for lists while they are offline). */
    record Member(UUID id, String name) {
        static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Member::id),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Member::name)
        ).apply(i, Member::new));
    }

    private record Cheer(UUID id, int rank) {
        static final Codec<Cheer> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Cheer::id),
                Codec.INT.fieldOf("rank").forGetter(Cheer::rank)
        ).apply(i, Cheer::new));
    }

    /** Schema 1 of the household (SPEC "Together"): a Hearth saved before it has none and loads with no members. */
    private record Household(int schema, List<Member> members, Optional<UUID> foundedFrom, List<Cheer> cheers) {
        static final int SCHEMA = 1;
        static final Household NONE = new Household(SCHEMA, List.of(), Optional.empty(), List.of());
        static final Codec<Household> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(Household::schema),
                Member.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(Household::members),
                UUIDUtil.CODEC.optionalFieldOf("founded_from").forGetter(Household::foundedFrom),
                Cheer.CODEC.listOf().optionalFieldOf("cheers", List.of()).forGetter(Household::cheers)
        ).apply(i, Household::new));
    }

    public static final Codec<HearthRecord> CODEC = RecordCodecBuilder.<HearthRecord>create(i -> i.group(
            Core.CODEC.fieldOf("core").forGetter(HearthRecord::core),
            Town.CODEC.fieldOf("town").forGetter(HearthRecord::town),
            Household.CODEC.optionalFieldOf("household", Household.NONE).forGetter(HearthRecord::household)
    ).apply(i, HearthRecord::fromSaved));

    public HearthRecord(UUID owner, String ownerName, BlockPos pos, String dimension) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.pos = pos;
        this.dimension = dimension;
    }

    private Core core() {
        return new Core(owner, ownerName, pos, dimension, tier, forgeTier, createdDay, pendingCelebration, Optional.ofNullable(spire),
                Optional.ofNullable(cottage));
    }

    private Town town() {
        List<Npc> n = new ArrayList<>();
        for (String id : arrived) n.add(new Npc(id, Optional.ofNullable(npcs.get(id))));
        List<Pad> p = new ArrayList<>();
        pads.forEach((pos, role) -> p.add(new Pad(pos, role, Optional.ofNullable(padEchoes.get(pos)), mineProgress.getOrDefault(pos, 0))));
        return new Town(n, p, List.copyOf(breaks.values()), List.of(itemsHauled, cropsHarvested, oresBrought));
    }

    private Household household() {
        List<Member> m = new ArrayList<>();
        members.forEach((id, name) -> m.add(new Member(id, name == null ? "" : name)));
        List<Cheer> c = new ArrayList<>();
        memberCelebrations.forEach((id, rank) -> c.add(new Cheer(id, rank)));
        return new Household(Household.SCHEMA, m, Optional.ofNullable(foundedFrom), c);
    }

    private static HearthRecord fromSaved(Core c, Town t, Household h) {
        HearthRecord r = fromParts(c, t);
        for (Member m : h.members) if (!m.id().equals(r.owner)) r.members.put(m.id(), m.name());
        r.foundedFrom = h.foundedFrom.orElse(null);
        for (Cheer ch : h.cheers) if (r.members.containsKey(ch.id())) r.memberCelebrations.put(ch.id(), ch.rank());
        return r;
    }

    private static HearthRecord fromParts(Core c, Town t) {
        HearthRecord r = new HearthRecord(c.owner, c.ownerName, c.pos, c.dimension);
        r.tier = Math.max(1, Math.min(MAX_TIER, c.tier));
        r.forgeTier = Math.max(1, Math.min(MAX_FORGE_TIER, c.forgeTier));
        r.createdDay = c.createdDay;
        r.pendingCelebration = c.pendingCelebration;
        r.spire = c.spire.orElse(null);
        r.cottage = c.cottage.orElse(null);
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

    /** Whether this player owns the Hearth or is one of its members. */
    public boolean belongs(UUID player) {
        return owner.equals(player) || members.containsKey(player);
    }

    /** The owner and every member, the owner first. */
    public List<UUID> everyone() {
        List<UUID> out = new ArrayList<>(1 + members.size());
        out.add(owner);
        out.addAll(members.keySet());
        return out;
    }

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
