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

/** Night and Weather (wait): things that only happen at the right hour or in the right weather. */
final class Weather {
    private Weather() {}

    static void init() {
        Kind.of(FSet.WEATHER, 1, "sitting_stone", "The Sitting Stone").verb(Verb.WAIT).curio().rank(0).anchor(Anchor.HILLTOP).made(Made.BUILT)
                .see("A stone worn into a seat on top of a rise, and a flat slab set in the turf beside it.")
                .learned("Somebody sat here for the sunrise, often. The first light lifted the slab.")
                .reach(6).slope(3).icon(Props.CAP_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    BlockState seat = m.stairs().hasProperty(StairBlock.FACING) ? m.stairs().setValue(StairBlock.FACING, Direction.NORTH) : m.stairs();
                    s.partOn("seat", 0, 0, 0, seat);
                    s.on(-1, 0, m.slab());
                    s.partOn("cache", 2, 1, 0, Props.CAP_STONE.get().of(0));
                    Dress.moss(s, -1, 0, 1, 2);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> When.stay("seat", When.DAWN, 20))
                .play(t -> t.hour(23500).stand("seat", 0).ticks(90))
                .add();
    }
}
