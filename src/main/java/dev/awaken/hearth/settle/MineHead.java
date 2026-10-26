package dev.awaken.hearth.settle;

import dev.awaken.Awaken;
import dev.awaken.delve.Ore;
import dev.awaken.delve.api.Lodes;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.room.Tally;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Your own mine (SPEC §28.8, PLAN 7.7, decision 8: "the Mine Echo works a real mine head instead of rolling dice"). A
 * Mine head room's shaft (a hatch over 16+ blocks of climbable shaft, {@code RoomType.MINE_HEAD}) is read for its depth and
 * for any lode it cuts (W2's {@code Lodes}: a lode block within three of the shaft anywhere down it). Each evening a miner
 * settler (or a Mine Echo on a Mine pad) brings up the ore of that depth, and if the shaft cut a lode, that lode's ore
 * too, until the mine has taken half the lode's blocks. Dig deeper, find a lode, and your mine gets richer.
 */
public final class MineHead {
    private MineHead() {}

    /** One shaft: its hatch, how deep it goes, the y of its foot, and the lode it cuts (or none). */
    public record Shaft(BlockPos hatch, int depth, int foot, Optional<Lodes.View> lode) {}

    private static final Map<Long, Optional<Lodes.View>> LODES = new HashMap<>();

    public static void clear() {
        LODES.clear();
    }

    /** Every Mine head's shaft at this Hearth (from the last scan), deepest first. */
    public static List<Shaft> shafts(ServerLevel level, HearthRecord h) {
        List<Shaft> out = new ArrayList<>();
        for (Room r : h.rooms) {
            if (!r.types().contains(RoomType.MINE_HEAD)) continue;
            int depth = r.fact(Tally.F_SHAFT);
            BlockPos hatch = new BlockPos(r.fact(Tally.F_SHAFT_X), r.fact(Tally.F_SHAFT_Y), r.fact(Tally.F_SHAFT_Z));
            out.add(new Shaft(hatch, depth, hatch.getY() - depth, lode(level, hatch, depth)));
        }
        out.sort((a, b) -> Integer.compare(b.depth, a.depth));
        return out;
    }

    /** The lode a shaft cuts (cached by hatch and depth): the nearest lode block within three of it anywhere down it. */
    static Optional<Lodes.View> lode(ServerLevel level, BlockPos hatch, int depth) {
        long key = hatch.asLong() * 31 + depth;
        Optional<Lodes.View> known = LODES.get(key);
        if (known != null) return known;
        Optional<Lodes.View> best = Optional.empty();
        for (int dy = 2; dy <= depth; dy += 4) {
            BlockPos p = hatch.below(dy);
            for (Ore ore : Ore.values()) {
                Optional<Lodes.View> v = Lodes.nearest(level, p, ore, 3);
                if (v.isPresent() && (best.isEmpty() || worth(v.get().ore()) > worth(best.get().ore()))) best = v;
            }
        }
        if (LODES.size() > 256) LODES.clear();
        LODES.put(key, best);
        return best;
    }

    /** How much a lode's ore is worth to a mine (the richer lode wins when a shaft cuts two). */
    private static int worth(Ore o) {
        return switch (o) {
            case DIAMOND, DEEPGLASS -> 9;
            case EMERALD -> 8;
            case GOLD -> 6;
            case REDSTONE, LAPIS -> 5;
            case IRON -> 4;
            case COPPER -> 2;
            case COAL -> 1;
        };
    }

    /** What a lode's ore comes up as. */
    public static Item item(Ore o) {
        return switch (o) {
            case COAL -> Items.COAL;
            case COPPER -> Items.RAW_COPPER;
            case IRON -> Items.RAW_IRON;
            case GOLD -> Items.RAW_GOLD;
            case REDSTONE -> Items.REDSTONE;
            case LAPIS -> Items.LAPIS_LAZULI;
            case EMERALD -> Items.EMERALD;
            case DIAMOND -> Items.DIAMOND;
            case DEEPGLASS -> BuiltInRegistries.ITEM.getValue(Awaken.id("deepglass_shard"));
        };
    }

    /** The depth's own words ("in the stone", "under the deepslate"), for the miner's lines and the guide. */
    public static String band(int foot) {
        if (foot >= 40) return "the upper stone";
        if (foot >= 16) return "the stone";
        if (foot >= 0) return "the deep stone";
        if (foot >= -24) return "the deepslate";
        return "the deep slate, far down";
    }

    /**
     * One evening's haul from a shaft whose foot is at {@code foot} (pure). The depth sets what comes up, as the lodes'
     * own bands do (PLAN 7.2): coal and copper high up, iron in the stone, gold, redstone and lapis in the deepslate, now
     * and then a diamond far down. {@code lode}: the lode's ore it cut and how much of it is left to take (0: none).
     */
    public static List<ItemStack> haul(RandomSource r, int foot, @Nullable Ore lode, int lodeLeft) {
        List<ItemStack> out = new ArrayList<>();
        if (foot >= 40) {
            add(out, Items.COAL, 3 + r.nextInt(3));
            add(out, Items.RAW_COPPER, 2 + r.nextInt(3));
        } else if (foot >= 16) {
            add(out, Items.COAL, 2 + r.nextInt(2));
            add(out, Items.RAW_COPPER, 2 + r.nextInt(2));
            add(out, Items.RAW_IRON, 1 + r.nextInt(2));
        } else if (foot >= 0) {
            add(out, Items.RAW_IRON, 2 + r.nextInt(3));
            add(out, Items.RAW_COPPER, 1 + r.nextInt(2));
            add(out, Items.COAL, 1 + r.nextInt(2));
            if (r.nextFloat() < 0.25f) add(out, Items.LAPIS_LAZULI, 1 + r.nextInt(2));
        } else if (foot >= -24) {
            add(out, Items.RAW_IRON, 2 + r.nextInt(2));
            add(out, Items.RAW_GOLD, 1 + r.nextInt(2));
            add(out, Items.REDSTONE, 2 + r.nextInt(3));
            if (r.nextFloat() < 0.3f) add(out, Items.LAPIS_LAZULI, 2 + r.nextInt(2));
        } else {
            add(out, Items.REDSTONE, 3 + r.nextInt(3));
            add(out, Items.RAW_GOLD, 1 + r.nextInt(3));
            add(out, Items.RAW_IRON, 1 + r.nextInt(2));
            if (r.nextFloat() < 0.3f) add(out, Items.LAPIS_LAZULI, 2 + r.nextInt(2));
            if (r.nextFloat() < 0.15f) add(out, Items.DIAMOND, 1);
        }
        if (lode != null && lodeLeft > 0) {
            int n = switch (lode) {
                case DIAMOND, EMERALD -> 1 + r.nextInt(2);
                case DEEPGLASS, GOLD -> 2 + r.nextInt(2);
                default -> 3 + r.nextInt(3);
            };
            add(out, item(lode), Math.min(n, lodeLeft));
        }
        return out;
    }

    private static void add(List<ItemStack> out, Item item, int n) {
        if (n > 0 && item != Items.AIR) out.add(new ItemStack(item, n));
    }

    /** What a lode gives a mine in all: half the blocks it was laid with. */
    public static int lodeBudget(Lodes.View v) {
        return Math.max(4, v.size() / 2);
    }
}
