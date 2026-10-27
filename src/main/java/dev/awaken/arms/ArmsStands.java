package dev.awaken.arms;

import dev.awaken.core.api.ArmsApi;
import dev.awaken.core.api.CoreApi;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Pattern Stands and the village smith's rack (docs/arms-progression/PLAN.md §3.4 doors 2 and 3). A structure's own
 * module places a stand through {@link ArmsApi#placeStand}; its style is drawn from its rank's band by the structure's seed,
 * so it shows the same one every time. A fighter takes it once (the claim id is the stand's position); a style they know
 * pays its price in essence instead. A stand glints and says what it holds when a fighter who hasn't taken it comes near.
 */
public final class ArmsStands {
    private ArmsStands() {}

    static final String[] PREFIX = {"wildhide", "beastbound", "gatewrought", "veilwoven", "lordscale", "sovereign"};
    private static final int CUE_RANGE_SQ = 7 * 7;
    /** The stands in loaded chunks, per dimension, so the cue never scans the world. */
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED = new HashMap<>();
    /** Stands already cued to a fighter this session (not saved: the cue may repeat after a relog). */
    private static final Map<UUID, Set<Long>> CUED = new HashMap<>();

    /** The style a stand holds when it is Hale's rack: it hangs the Wildhide weapons and opens his Armoury (the tier field is the first of its three hooks). */
    public static final String HALE = "hale";
    /** How many weapons one of Hale's racks hangs, and how many racks stand. */
    public static final int RACK_SLOTS = 3, RACKS = 2;

    /** The block entity type of a stand (for its renderer). */
    public static net.minecraft.world.level.block.entity.BlockEntityType<PatternStandBlockEntity> standType() {
        return ArmsContent.STAND_BE.get();
    }

    static void loaded(Level level, BlockPos pos) {
        LOADED.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.asLong());
    }

    static void unloaded(Level level, BlockPos pos) {
        Set<Long> at = LOADED.get(level.dimension());
        if (at != null) at.remove(pos.asLong());
    }

    static void forget(UUID player) {
        CUED.remove(player);
    }

    /** The style a stand of this rank holds, drawn by the seed: the rank's band, else the highest rank below it that has styles. */
    static ArmsHomes.@Nullable Home choose(Rank rank, long seed) {
        for (int r = Math.min(rank.ordinal(), Rank.S.ordinal()); r >= 0; r--) {
            List<ArmsHomes.Home> at = ArmsHomes.atRank(Rank.values()[r]);
            if (at.isEmpty()) continue;
            long h = seed * 0x9E3779B97F4A7C15L;
            return at.get((int) Math.floorMod(h ^ (h >>> 29), (long) at.size()));
        }
        return null;
    }

    /** Places a stand: the block (its make and facing), the style the seed draws at the rank, the weapon tier of the place. */
    static boolean place(LevelAccessor level, BlockPos pos, Direction facing, String kind, String rankLabel, long seed) {
        var state = ArmsContent.STAND.get().defaultBlockState().setValue(PatternStandBlock.FACING, facing)
                .setValue(PatternStandBlock.KIND, StandKind.of(kind));
        level.setBlock(pos, state, 2);
        if (!(level.getBlockEntity(pos) instanceof PatternStandBlockEntity be)) return false;
        if (rankLabel.isEmpty()) {
            be.pend(seed); // the land's level is read on the server thread, when someone first comes near
            return true;
        }
        Rank rank = rank(rankLabel);
        ArmsHomes.Home home = choose(rank, seed);
        if (home == null) return false;
        be.set(home.style(), Math.min(5, rank.ordinal()));
        return true;
    }

    /** A stand placed without a rank draws its pattern now, from the rank of the land it stands in. */
    static void resolve(ServerLevel level, BlockPos pos, PatternStandBlockEntity be) {
        if (!be.pending()) return;
        Rank rank = ArmsHomes.rankOfLevel(dev.awaken.living.api.Regions.levelAt(level, pos.getX(), pos.getZ()));
        ArmsHomes.Home home = choose(rank, be.seed());
        if (home != null) be.set(home.style(), Math.min(5, rank.ordinal()));
    }

    static Rank rank(String label) {
        for (Rank r : Rank.values()) if (r.label.equals(label)) return r;
        return Rank.E;
    }

    /** Hale's rack was used: his first-weapon pick while it waits, else his Armoury (the same as speaking to him while sneaking). */
    static void useHaleRack(ServerPlayer p) {
        if (ArmsApi.issuePending(p)) ArmsApi.openIssue(p);
        else ArmsApi.openRack(p);
    }

    /**
     * Two racks beside Hale, turned to face him, three hooks each: the five Wildhide weapons Hale's rack always has for
     * essence. Placed once (nothing is added where a rack of his already stands within 6 blocks); the first free pair of
     * cells 3 blocks out on solid ground, east first. False when there is no room.
     */
    static boolean placeHaleRack(ServerLevel level, BlockPos hale) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -6; dz <= 6; dz++) {
                    m.set(hale.getX() + dx, hale.getY() + dy, hale.getZ() + dz);
                    if (level.hasChunkAt(m) && level.getBlockEntity(m) instanceof PatternStandBlockEntity be && be.style().equals(HALE)) return true;
                }
        for (Direction d : new Direction[] {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH}) {
            for (int dy : new int[] {0, 1, -1}) {
                BlockPos first = hale.relative(d, 3).above(dy), second = first.relative(d.getClockWise());
                if (!rackCell(level, first) || !rackCell(level, second)) continue;
                for (int i = 0; i < RACKS; i++) {
                    BlockPos at = i == 0 ? first : second;
                    level.setBlock(at, ArmsContent.STAND.get().defaultBlockState().setValue(PatternStandBlock.FACING, d.getOpposite())
                            .setValue(PatternStandBlock.KIND, StandKind.RACK), 2);
                    if (level.getBlockEntity(at) instanceof PatternStandBlockEntity be) be.set(HALE, i * RACK_SLOTS);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean rackCell(ServerLevel level, BlockPos at) {
        return level.hasChunkAt(at) && level.getBlockState(at).canBeReplaced() && level.getBlockState(at.above()).isAir() && level.getBlockState(at.below()).isSolid();
    }

    /** The weapon a stand shows: its tier's weapon of its style. */
    public static ItemStack weapon(String style, int tier) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(dev.awaken.Awaken.id(PREFIX[Math.max(0, Math.min(5, tier))] + "_" + style));
        return item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    static long claimKey(ServerLevel level, BlockPos pos) {
        return level.dimension() == Level.OVERWORLD ? pos.asLong() : pos.asLong() ^ (level.dimension().identifier().hashCode() * 0x9E3779B97F4A7C15L);
    }

    static boolean taken(ServerPlayer p, ServerLevel level, BlockPos pos) {
        return ArmsProgress.of(p).claimedStands.contains(claimKey(level, pos));
    }

    /** A fighter uses a stand with an empty hand: the pattern, once; a style they know pays its price in essence. */
    static void take(ServerPlayer p, ServerLevel level, BlockPos pos, PatternStandBlockEntity be) {
        if (be.style().equals(HALE)) {
            useHaleRack(p);
            return;
        }
        resolve(level, pos, be);
        String style = be.style();
        if (style.isEmpty()) return;
        if (taken(p, level, pos)) {
            Signal.toast(p, Component.literal("You have already taken the pattern from this stand."));
            return;
        }
        dev.awaken.progression.Progression.count(p, "awaken:patterns_from_stands");
        ArmsProgress g = ArmsProgress.of(p);
        g.claimedStands.add(claimKey(level, pos));
        p.setData(ArmsContent.PROGRESS, g);
        ArmsHomes.Home home = ArmsHomes.of(style);
        if (!ArmsIssue.INSTANCE.learn(p, style, "from a Pattern Stand")) {
            int price = ArmsHomes.rackPrice(home == null ? Rank.E : home.rank());
            ArmsIssue.giveItem(p, new ItemStack(CoreApi.ESSENCE_SHARD.get(), price));
            Signal.toast(p, Component.literal("You already know the " + ArmsIssue.optionName(style) + ", so the stand pays " + price + " essence instead."));
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    /** Near a stand you haven't taken from: it says what it holds, once (the find cue). */
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 20 != 0) return;
        Set<Long> at = LOADED.get(p.level().dimension());
        if (at == null || at.isEmpty()) return;
        ServerLevel level = (ServerLevel) p.level();
        for (long packed : at) {
            BlockPos pos = BlockPos.of(packed);
            if (pos.distSqr(p.blockPosition()) > CUE_RANGE_SQ) continue;
            if (CUED.computeIfAbsent(p.getUUID(), k -> new HashSet<>()).contains(packed) || taken(p, level, pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof PatternStandBlockEntity be) || be.style().equals(HALE)) continue;
            resolve(level, pos, be);
            if (be.style().isEmpty()) continue;
            CUED.get(p.getUUID()).add(packed);
            String name = ArmsIssue.optionName(be.style());
            Signal.toast(p, Component.literal("PATTERN · ").withColor(0x6FB3FF).append(Component.literal((name == null ? be.style() : name) + " · use to take it").withColor(0xD6ECFF)));
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
    }

    // ---- the village smith's rack ----------------------------------------------------------------------------------

    /**
     * What a village smith sells on his rack: two patterns of the village's rank, drawn by its seed and never one the
     * fighter knows (a rank that is all known sells the next one down), priced in emeralds by the rank.
     */
    static List<ArmsApi.VillageOffer> villagePatterns(ServerPlayer p, long seed, int landLevel) {
        Rank rank = ArmsHomes.rankOfLevel(landLevel);
        List<ArmsApi.VillageOffer> out = new ArrayList<>();
        for (int r = Math.min(rank.ordinal(), Rank.S.ordinal()); r >= 0 && out.size() < 2; r--) {
            List<ArmsHomes.Home> band = new ArrayList<>(ArmsHomes.atRank(Rank.values()[r]));
            if (band.isEmpty()) continue;
            int start = (int) Math.floorMod(seed * 0x9E3779B97F4A7C15L >>> 17, (long) band.size());
            for (int i = 0; i < band.size() && out.size() < 2; i++) {
                ArmsHomes.Home h = band.get((start + i) % band.size());
                Item blueprint = ArmsItems.blueprintItem(h.style());
                if (blueprint == null || ArmsIssue.INSTANCE.knows(p, h.style())) continue;
                out.add(new ArmsApi.VillageOffer(h.style(), new ItemStack(blueprint), ArmsHomes.villagePrice(Rank.values()[r])));
            }
        }
        return out;
    }

    /** The smith's pattern was paid for: it is known at once (the Blueprint came with the trade). */
    static void villageBought(ServerPlayer p, String style) {
        ArmsIssue.remember(p, style);
        dev.awaken.progression.Progression.count(p, "awaken:patterns_from_villages");
        String name = ArmsIssue.optionName(style);
        Signal.toast(p, Component.literal("PATTERN · ").withColor(0x6FB3FF).append(Component.literal((name == null ? style : name) + " Blueprint · from the village smith").withColor(0xD6ECFF)));
        Progression.markNewThing(p, "pattern:" + style);
        ArmsArmoury.sendView(p);
    }
}
