package dev.awaken.finds.sets;

import dev.awaken.finds.Kind;
import dev.awaken.finds.Kind.Anchor;
import dev.awaken.finds.Kind.FSet;
import dev.awaken.finds.Kind.Made;
import dev.awaken.finds.Kind.Tell;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.Dress;
import dev.awaken.finds.kit.Mats;
import dev.awaken.finds.kit.Props;
import dev.awaken.finds.verb.*;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** High Things (reach): something you can see and can't easily get to. */
final class High {
    private High() {}

    private static final int[] SX = {1, 0, -1, 0}, SZ = {0, 1, 0, -1};

    static void init() {
        Kind.of(FSet.HIGH, 1, "glint_in_the_crown", "The Glint in the Crown").verb(Verb.REACH).curio().rank(0).lands(Group.FOREST, Group.DARK, Group.JUNGLE, Group.PLAINS).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("Something flashing in the top of the tallest tree around.")
                .learned("A magpie's hoard. The broken branches made a stair, if you didn't look down.")
                .reach(6).tell(Tell.GLINT).icon(Props.NEST)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    BlockState log = m.log(), leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                    s.column(0, 0, 0, 12, log);
                    for (int k = 0; k <= 11; k++) {
                        int d = k & 3;
                        BlockState stub = log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, SX[d] != 0 ? Direction.Axis.X : Direction.Axis.Z) : log;
                        s.up(SX[d], SZ[d], k, stub);
                    }
                    Dress.crown(s, 0, 0, 12, 3, leaf);
                    for (int k = 12; k <= 15; k++) s.air(0, s.g(0, 0) + 1 + k, -1);
                    s.partOn("prize", 0, 0, 13, Props.NEST.get().of(2));
                    s.partOn("cache", 0, 0, 14);
                    s.tell(0, 0, 14);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Prize.at("prize"))
                .play(t -> t.use("prize", 0))
                .add();
    }
}
