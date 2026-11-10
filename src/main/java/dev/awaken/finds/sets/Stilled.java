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

/** The Stilled at Rest (carry): stone people stopped in the middle of something, and the thing they're missing. */
final class Stilled {
    private Stilled() {}

    static void init() {
        Kind.of(FSet.STILLED, 1, "stilled_fisher", "The Stilled Fisher").verb(Verb.CARRY).curio().rank(0).anchor(Anchor.SHORE).made(Made.STILLED)
                .see("A stone figure sitting on the bank with its hands held out, closed round nothing. A rod lies in the reeds.")
                .learned("It was fishing when it stopped. With the rod back in its hands, the float bobbed once.")
                .reach(8).slope(3).icon(Props.OLD_ROD)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    s.on(0, 0, Props.STILLED.get().front(2, Direction.SOUTH));
                    s.partOn("loose", -3, -1, 0, Props.OLD_ROD.get().of(0));
                    s.partOn("socket", 0, 1, 0);
                    s.part("cache", 0, s.sea() + 1, 4);
                    for (int i = 0; i < 7; i++) {
                        int x = -4 + s.rnd.nextInt(9), z = -2 + s.rnd.nextInt(4);
                        if (s.depth(x, z) == 0 && !s.taken(x, s.g(x, z) + 1, z)) s.on(x, z, (s.rnd.nextBoolean() ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState());
                    }
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Sockets.need("socket", st -> st.is(Props.OLD_ROD.get().asItem()), i -> Props.OLD_ROD.get().front(0, Direction.SOUTH)).carry("loose"))
                .play(t -> t.use("loose", 0).hold(Props.OLD_ROD.get()).use("socket", 0))
                .add();
    }
}
