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

/** Cairns (stack): stones set on stones. */
final class Cairns {
    private Cairns() {}

    static void init() {
        Kind.of(FSet.CAIRNS, 1, "wanderers_cairn", "The Wanderer's Cairn").verb(Verb.STACK).curio().rank(0).anchor(Anchor.HILLTOP).made(Made.BUILT)
                .see("A knee-high stack of flat stones on a rise, with one more lying in the grass beside it.")
                .learned("Somebody marked this rise for whoever came after. It's a stone taller for you.")
                .reach(7).slope(3).persists().icon(Props.CAIRN)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    Dress.cairn(s, 0, 0, 2, 2);
                    s.partOn("top", 0, 0, 1);
                    s.partOn("loose", 2, 1, 0, Props.LOOSE_STONE.get().of(s.rnd.nextInt(4)));
                    s.partOn("cache", -1, 1, 0);
                    Dress.scatter(s, 0, 0, 3, 4, () -> Dress.pebbles(s));
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Fit.on("top", st -> st.is(Props.LOOSE_STONE.get().asItem()), 1).carry("loose"))
                .play(t -> t.use("loose", 0).hold(Props.LOOSE_STONE.get()).use("top", 0))
                .add();
    }
}
