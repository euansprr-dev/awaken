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

/** Mended Things (mend): something broken that works again once it has its missing piece. */
final class Mended {
    private Mended() {}

    static void init() {
        Kind.of(FSet.MENDED, 1, "broken_signpost", "The Broken Signpost").verb(Verb.MEND).curio().rank(0).anchor(Anchor.LINE).length(20).made(Made.FALLEN)
                .see("A signpost beside an old road, with its three arms lying on the ground.")
                .learned("Each arm is carved with a shape, not a name. With all three back, it points again.")
                .reach(10).persists().icon(Props.SIGNPOST)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    Dress.road(s, -9, 9, 1, m);
                    s.partOn("post", 2, 0, 0, Props.SIGNPOST.get().of(0));
                    int[][] at = {{-2, 3}, {3, -4}, {-3, -2}};
                    for (int i = 0; i < 3; i++) s.partOn("loose", at[i][0], at[i][1], 0, Props.SIGN_ARM.get().of(i, Direction.from2DDataValue(s.rnd.nextInt(4))));
                    s.partOn("cache", 3, 1, 0);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Fit.on("post", st -> st.is(Props.SIGN_ARM.get().asItem()), 3).carry("loose"))
                .play(t -> { for (int i = 0; i < 3; i++) t.use("loose", i); for (int i = 0; i < 3; i++) t.hold(Props.SIGN_ARM.get()).use("post", 0); })
                .add();
    }
}
