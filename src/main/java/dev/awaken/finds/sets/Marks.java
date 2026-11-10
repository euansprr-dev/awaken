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

/** Marks (shoot): things to hit from a distance. */
final class Marks {
    private Marks() {}

    static void init() {
        Kind.of(FSet.MARKS, 1, "gourd_tree", "The Gourd Tree").verb(Verb.SHOOT).curio().rank(0).lands(Group.FOREST, Group.PLAINS, Group.JUNGLE).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A tree hung with painted gourds on strings, swinging.")
                .learned("Somebody practised here. All five down in ten shots, and the last one had something in it.")
                .reach(7).icon(Props.GOURD)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                    s.column(0, 0, 0, 5, m.log());
                    for (int x = -3; x <= 3; x++)
                        for (int z = -3; z <= 3; z++)
                            for (int k = 4; k <= 6; k++) {
                                int d = Math.abs(x) + Math.abs(z) + (k - 4);
                                if ((x != 0 || z != 0 || k == 6) && d <= 4 && (d < 4 || s.rnd.nextBoolean())) s.up(x, z, k, leaf);
                            }
                    int[][] at = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}};
                    for (int i = 0; i < 5; i++) s.partOn("mark", at[i][0], at[i][1], 3, Props.GOURD.get().of(i & 3));
                    s.partOn("cache", 1, 0, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> dev.awaken.finds.verb.Marks.hit("mark").shots(10))
                .play(t -> { for (int i = 0; i < t.count("mark"); i++) t.shoot("mark", i); })
                .add();
    }
}
