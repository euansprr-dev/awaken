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

/** Ringing Stones (ring): things that sound when struck, and the order they want. */
final class Ringing {
    private Ringing() {}

    static void init() {
        Kind.of(FSet.RINGING, 1, "three_stones", "The Three Stones").verb(Verb.RING).curio().rank(0).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("Three standing stones of different heights. Tap one and it rings.")
                .learned("The wind plays them. Play it back and the tallest opens.")
                .reach(6).tell(Tell.CHIME).icon(Props.STANDING_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    int[] xs = {-3, 0, 3}, zs = {0, 1, -1};
                    for (int i = 0; i < 3; i++) {
                        for (int k = 0; k < i; k++) s.up(xs[i], zs[i], k, k == 0 ? m.mossy() : m.stone());
                        s.partOn("stone", xs[i], zs[i], i, Props.STANDING_STONE.get().of(i == 1 ? 1 : 0, Direction.from2DDataValue(s.rnd.nextInt(4))));
                    }
                    s.partOn("cache", 3, 0, 0);
                    s.tell(0, 1, 3);
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Notes.of("stone", 3))
                .play(t -> { for (int i : Notes.order(t.seed(), 3, 3)) t.use("stone", i); })
                .add();
    }
}
