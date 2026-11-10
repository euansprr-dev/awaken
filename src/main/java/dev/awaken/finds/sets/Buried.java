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

/** Buried Things (dig): what's under the ground, and what gives it away. */
final class Buried {
    private Buried() {}

    static void init() {
        Kind.of(FSet.BURIED, 1, "foxs_dig", "The Fox's Dig").verb(Verb.DIG).curio().rank(0).lands(Group.PLAINS, Group.FOREST, Group.SNOW, Group.DARK).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("A small fox digging hard at one spot. It runs when it sees you.")
                .learned("It could smell something. You finished the hole.")
                .reach(6).icon(() -> net.minecraft.world.item.Items.SWEET_BERRIES)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.put(0, 0, 0, Blocks.AIR.defaultBlockState());
                    s.put(0, -1, 0, Blocks.COARSE_DIRT.defaultBlockState());
                    s.part("spot", 0, -1, 0);
                    s.part("cache", 0, 0, 0);
                    s.floor(1, 0, Blocks.COARSE_DIRT.defaultBlockState());
                    s.floor(0, 1, Blocks.COARSE_DIRT.defaultBlockState());
                    s.floor(-1, 1, Blocks.ROOTED_DIRT.defaultBlockState());
                    s.on(1, 0, Dress.pebbles(s));
                    s.on(-1, 1, Dress.twigs(s));
                    s.spawn(net.minecraft.world.entity.EntityTypes.FOX, 1, 1, 0, "fox");
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Dig.at("spot"))
                .play(t -> t.dig("spot", 0))
                .add();
    }
}
