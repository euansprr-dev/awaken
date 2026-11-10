package dev.awaken.finds.sets;

import dev.awaken.finds.Family;
import dev.awaken.finds.Kind;
import dev.awaken.finds.Kind.Anchor;
import dev.awaken.finds.Kind.FSet;
import dev.awaken.finds.Kind.Made;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.M;
import dev.awaken.finds.kit.Props;
import dev.awaken.finds.verb.Ctx;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.finds.kit.B.*;

/**
 * What the twelve families share (SPEC §62): how one of their five is registered, the small things every monument has
 * (a plinth with its prize, a carving, a door that lets go), and what later people left at its foot.
 */
final class Mon {
    private Mon() {}

    private static final String[] HOARD = {"hoard_e", "hoard_d", "hoard_c", "hoard_b", "hoard_a", "hoard_s"};

    static final Group[] EVERYWHERE = {Group.PLAINS, Group.FOREST, Group.SNOW, Group.DESERT, Group.BADLANDS};

    /** One of a family's five: a monument on a terrace, its rank from its tier, its hoard from its rank, its relic for an icon. */
    static Kind.Builder kind(Family f, int tier, String id, String name) {
        return Kind.of(FSet.MONUMENTS, f.ordinal() * 5 + tier, id, name).place().family(f, tier).made(Made.FALLEN).anchor(Anchor.TERRACE).slope(14)
                .loot(HOARD[f.rankOf(tier)]).icon(f::relic);
    }

    /** 0..n-1, the same for every chunk of one site and different from site to site: which arc fell, who camped here. */
    static int pick(Scene s, int salt, int n) {
        return h(s, salt, 131, salt * 7 + 3) % n;
    }

    /** A carving: two carved stones one over the other, the lower one the part. */
    static void carving(Scene s, int x, int y, int z) {
        s.put(x, y, z, M.glyph());
        s.put(x, y + 1, z, M.glyph());
        s.part("carving", x, y, z);
    }

    /** The prize on its plinth: a dressed block with a step up to it from the front, the seal on top. */
    static void prize(Scene s, int x, int y, int z, Direction front) {
        s.put(x, y, z, M.block());
        s.put(x, y + 1, z, M.seal());
        s.part("prize", x, y + 1, z);
        Direction l = front.getClockWise(), r = front.getCounterClockWise();
        s.put(x + l.getStepX(), y, z + l.getStepZ(), stair(M.blockStairs(), r));
        s.put(x + r.getStepX(), y, z + r.getStepZ(), stair(M.blockStairs(), l));
        s.put(x + front.getStepX(), y, z + front.getStepZ(), stair(M.blockStairs(), front.getOpposite()));
    }

    /** Every part of a name gives way (a door of the old world lets go). */
    static void open(Ctx c, String part, SoundEvent sound) {
        for (int i = 0; i < c.count(part); i++) c.set(part, i, AIR);
        if (c.count(part) > 0) c.sound(c.pos(part, 0), sound, 3.0F, 0.5F);
    }

    /** Every part of a name becomes a state (lamps that come on, a channel that fills). */
    static void all(Ctx c, String part, BlockState to) {
        for (int i = 0; i < c.count(part); i++) c.set(part, i, c.turned(to));
    }

    // ---- what later people left (SPEC §62.5): one of five, by the site ---------------------------------------------------

    /** On the ground round (x, z), about seven by six; it levels a small pad for itself. */
    static void camp(Scene s, int x, int z) {
        int kind = pick(s, 5, 5);
        if (kind == 4) return;                                   // nobody for a long time
        int g = s.g(x, z);
        s.level(x, z, 3, 2, g);
        int y = g + 1;
        switch (kind) {
            case 0 -> {                                          // climbers: a tent, a cold fire, their rope
                BlockState cloth = st(Blocks.WOOL.pick(DyeColor.LIGHT_GRAY));
                for (int k = -1; k <= 2; k++) {
                    s.put(x - 2, y, z + k, cloth);
                    s.put(x + 2, y, z + k, cloth);
                    s.put(x - 1, y + 1, z + k, cloth);
                    s.put(x + 1, y + 1, z + k, cloth);
                    s.put(x, y + 2, z + k, cloth);
                    s.put(x - 2, y + 1, z + k, st(Blocks.WOOL_SLAB.pick(DyeColor.LIGHT_GRAY)));   // the eaves, which hold the slopes to the walls
                    s.put(x + 2, y + 1, z + k, st(Blocks.WOOL_SLAB.pick(DyeColor.LIGHT_GRAY)));
                }
                s.put(x, y, z - 1, cloth);
                s.put(x, y + 1, z - 1, cloth);
                s.put(x, y, z, st(Blocks.CARPET.pick(DyeColor.RED)));
                s.put(x, y, z + 1, st(Blocks.CARPET.pick(DyeColor.BROWN)));
                s.put(x + 3, y - 1, z + 3, st(Blocks.COBBLESTONE));
                s.put(x + 3, y, z + 3, st(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, false));
                s.put(x - 3, y, z + 2, Props.ROPE_COIL.get().of(0));
                s.put(x + 3, y, z, Props.BUNDLE.get().of(1));
            }
            case 1 -> {                                          // herders: a lean-to, hay, a barrel
                for (int k = -2; k <= 2; k += 4) for (int j = 0; j <= 1; j++) s.put(x + k, y + j, z + 1, st(Blocks.SPRUCE_FENCE));
                for (int k = -3; k <= 3; k++) {
                    s.put(x + k, y + 2, z + 1, st(Blocks.SPRUCE_SLAB));
                    s.put(x + k, y + 1, z, top(st(Blocks.SPRUCE_SLAB)));
                    s.put(x + k, y + 1, z - 1, st(Blocks.SPRUCE_SLAB));
                }
                for (int k = -3; k <= 3; k += 6) s.put(x + k, y, z - 1, st(Blocks.SPRUCE_PLANKS));
                s.put(x - 1, y, z, st(Blocks.HAY_BLOCK));
                s.put(x, y, z, st(Blocks.CARPET.pick(DyeColor.BROWN)));
                s.put(x + 2, y, z + 2, st(Blocks.BARREL));
                s.put(x - 2, y - 1, z + 3, st(Blocks.COBBLESTONE));
                s.put(x - 2, y, z + 3, st(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, false));
            }
            case 2 -> {                                          // pilgrims: a stone with candles, ribbons on a dead tree
                s.put(x, y, z, M.block());
                s.put(x, y + 1, z, candles(3, true));
                s.put(x - 1, y, z, candles(1, true));
                s.put(x + 1, y, z + 1, candles(2, false));
                s.put(x, y, z + 1, Props.STONE_BOWL.get().of(0));
                BlockState dead = st(Blocks.STRIPPED_OAK_LOG);
                for (int k = 0; k <= 3; k++) s.put(x + 3, y + k, z - 1, dead);
                s.put(x + 2, y + 3, z - 1, st(Blocks.OAK_FENCE));
                s.put(x + 4, y + 2, z - 1, st(Blocks.OAK_FENCE));
                s.put(x + 3, y + 3, z, st(Blocks.OAK_FENCE));
                s.put(x + 2, y + 2, z - 1, st(Blocks.WOOL.pick(DyeColor.RED)));
                s.put(x + 4, y + 1, z - 1, st(Blocks.WOOL.pick(DyeColor.YELLOW)));
                s.put(x + 3, y + 2, z, st(Blocks.WOOL.pick(DyeColor.LIGHT_BLUE)));
            }
            default -> {                                         // a hermit: a hut of boards against the weather
                BlockState wall = st(Blocks.SPRUCE_PLANKS), post = st(Blocks.STRIPPED_SPRUCE_LOG);
                for (int k = -2; k <= 2; k++)
                    for (int j = -2; j <= 2; j++) {
                        boolean edge = Math.abs(k) == 2 || Math.abs(j) == 2, corner = Math.abs(k) == 2 && Math.abs(j) == 2;
                        if (edge) for (int u = 0; u <= 2; u++) s.put(x + k, y + u, z + j, corner ? post : (k == 0 && j == 2 && u <= 1) ? AIR : (j == -2 && k == 0 && u == 1) ? st(Blocks.GLASS_PANE) : wall);
                        s.put(x + k, y + 3, z + j, edge ? st(Blocks.SPRUCE_SLAB) : wall);
                    }
                s.put(x - 1, y, z - 1, st(Blocks.HAY_BLOCK));
                s.put(x + 1, y, z - 1, st(Blocks.BARREL));
                s.put(x, y + 2, z, lantern(true, false));
                s.put(x + 3, y, z + 3, stack());
            }
        }
    }

    private static BlockState stack() {
        return axis(st(Blocks.SPRUCE_LOG), Direction.Axis.X);
    }
}
