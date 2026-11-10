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

/** Small Gods (offer): little shrines to animals, and what the animals leave in return. */
final class Gods {
    private Gods() {}

    static void init() {
        Kind.of(FSet.GODS, 1, "nine_cats", "The Shrine of Nine Cats").verb(Verb.OFFER).curio().rank(0).lands(Group.PLAINS, Group.FOREST, Group.DARK, Group.JUNGLE).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A knee-high stone shrine in long grass with a bowl in front of it, and cats asleep on every side.")
                .learned("They take fish. One of them brought you something it had found.")
                .reach(7).icon(Props.STONE_BOWL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    s.on(0, -1, m.wall());
                    s.up(0, -1, 1, m.slab());
                    s.partOn("bowl", 0, 0, 0, Props.STONE_BOWL.get().of(0));
                    s.partOn("cache", 0, 1, 0);
                    int cats = 5 + s.rnd.nextInt(5);
                    for (int i = 0; i < cats; i++) {
                        double a = i * 6.283 / cats + s.rnd.nextDouble() * 0.5, r = 2.2 + s.rnd.nextDouble() * 2.2;
                        s.spawn(net.minecraft.world.entity.EntityTypes.CAT, (int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r), Scene.Spawn.LIE | Scene.Spawn.NO_AI, "cat");
                    }
                    s.on(-2, -2, Blocks.DANDELION.defaultBlockState());
                    s.on(2, -1, Blocks.POPPY.defaultBlockState());
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Sockets.need("bowl", st -> st.is(ItemTags.FISHES), i -> Props.STONE_BOWL.get().of(1)))
                .play(t -> t.hold(net.minecraft.world.item.Items.COD).use("bowl", 0))
                .add();
    }
}
