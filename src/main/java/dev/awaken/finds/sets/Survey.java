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

/** Surveyor's Stones (read): marks somebody left, and what they add up to. */
final class Survey {
    private Survey() {}

    static void init() {
        Kind.of(FSet.SURVEY, 1, "surveyors_stone", "The Surveyor's Stone").verb(Verb.READ).curio().rank(0).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A squared stone on a block of rock, with a number, an arrow and a brass stud.")
                .learned("One of twelve. The arrow is cut toward the next, and the next is further out.")
                .reach(5).lead().icon(Props.MILESTONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    s.on(0, 0, m.stone());
                    s.partOn("stone", 0, 0, 1, Props.MILESTONE.get().of(3));
                    s.on(1, 0, m.slab());
                    Dress.moss(s, 1, 0, 1, 2);
                    s.partOn("cache", 0, 1, 0);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Read.all("stone"))
                .play(t -> t.use("stone", 0))
                .add();
    }
}
