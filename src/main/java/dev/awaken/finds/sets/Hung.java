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

/** Hung Things (pull): ropes, and what's on the other end. */
final class Hung {
    private Hung() {}

    static void init() {
        Kind.of(FSet.HUNG, 1, "hanging_bundle", "The Hanging Bundle").verb(Verb.PULL).curio().rank(0).lands(Group.FOREST, Group.DARK, Group.JUNGLE, Group.PLAINS).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A wrapped bundle hung high from a branch, on a rope that runs down to a peg.")
                .learned("A traveller hung it out of reach of animals and never came back. Lowered slowly, it didn't burst.")
                .reach(7).icon(Props.BUNDLE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    BlockState log = m.log(), branch = log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X) : log;
                    BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                    s.column(0, 0, 0, 6, log);
                    for (int x = 1; x <= 3; x++) s.up(x, 0, 5, branch);
                    for (int x = -2; x <= 4; x++)
                        for (int z = -2; z <= 2; z++)
                            for (int k = 6; k <= 7; k++)
                                if (!(x == 0 && z == 0 && k == 6) && Math.abs(x - 1) + Math.abs(z) + (k - 6) * 2 <= 4 && s.rnd.nextInt(6) != 0) s.up(x, z, k, leaf);
                    s.up(3, 0, 4, Props.ROPE_LENGTH.get().of(0));
                    s.partOn("load", 3, 0, 3, Props.BUNDLE.get().of(0));
                    for (int k = 2; k >= 0; k--) s.partOn("load", 3, 0, k);
                    s.partOn("rope", -2, 1, 0, Props.ROPE_PEG.get().of(0));
                    s.partOn("cache", 3, 0, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Haul.on("rope", "load").gently().trail(Props.ROPE_LENGTH.get().of(0)))
                .play(t -> t.use("rope", 0).pass(30).use("rope", 0).pass(30).use("rope", 0))
                .add();
    }
}
