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

/** Beast Works (sneak): what animals build, and how to be round them. */
final class Beasts {
    private Beasts() {}

    static boolean blue(net.minecraft.world.item.ItemStack st) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        return id.contains("blue") || id.contains("lapis") || id.contains("cornflower") || id.contains("orchid");
    }

    static void init() {
        Kind.of(FSet.BEASTS, 1, "magpie_bower", "The Magpie Bower").verb(Verb.SNEAK).curio().rank(0).lands(Group.FOREST, Group.PLAINS, Group.JUNGLE).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("An arch of woven sticks on the ground, with blue things laid out in front of it.")
                .learned("Take nothing. Add something blue, and it moves one of its own to your side.")
                .reach(6).icon(Props.HOARD)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    for (int x = -1; x <= 1; x += 2) s.column(x, 0, 0, 1, m.fence());
                    for (int x = -1; x <= 1; x++) s.up(x, 0, 2, m.woodSlab());
                    s.on(0, -1, Props.HOARD.get().of(0));
                    s.partOn("cache", 0, 0, 0);
                    s.partOn("socket", 0, 1, 0);
                    s.on(-1, 2, Blocks.CORNFLOWER.defaultBlockState());
                    s.on(1, 1, Blocks.BLUE_CARPET.defaultBlockState());
                    s.on(2, 2, Dress.twigs(s));
                    s.spawn(EntityType.PARROT, 0, -2, 0, "bird");
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Sockets.need("socket", Beasts::blue, i -> Blocks.BLUE_CARPET.defaultBlockState()))
                .play(t -> t.hold(net.minecraft.world.item.Items.LAPIS_LAZULI).use("socket", 0))
                .add();
    }
}
