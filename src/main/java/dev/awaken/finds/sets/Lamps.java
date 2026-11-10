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

/** Lamplighter's Road (light): lamps that somebody used to light. */
final class Lamps {
    private Lamps() {}

    static void init() {
        Kind.of(FSet.LAMPS, 1, "cold_lamps", "The Cold Lamps").verb(Verb.LIGHT).curio().rank(0).anchor(Anchor.LINE).length(52).made(Made.BUILT)
                .see("Five iron lamps on old posts along a stretch of road, all dark.")
                .learned("Somebody lit these every evening. The road went somewhere worth seeing by night.")
                .reach(27).tell(Tell.MOTHS).icon(Props.IRON_LAMP)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    Dress.road(s, -24, 26, 1, m);
                    int z = -20;
                    for (int i = 0; i < 5; i++) {
                        int x = i % 2 == 0 ? 2 : -2;
                        s.on(x, z, m.wall());
                        s.up(x, z, 1, Props.POST.get().of(0));
                        s.partOn("lamp", x, z, 2, Props.IRON_LAMP.get().of(0));
                        Dress.moss(s, x + (x > 0 ? 1 : -1), z, 0, 3);
                        if (i == 0) s.tell(x, z, 3);
                        if (i == 2 && s.rnd.nextBoolean()) s.on(x + (x > 0 ? 1 : -1), z, Props.CART_WHEEL.get().of(0, Direction.EAST));
                        if (i == 4) s.partOn("cache", x, z + 1, 0, Props.FOOTING.get().front(0, x > 0 ? Direction.WEST : Direction.EAST));
                        z += 9 + s.rnd.nextInt(3);
                    }
                    s.on(3, -22, Props.MILESTONE.get().of(s.rnd.nextInt(3)));
                })
                .script(() -> Light.all("lamp").within(60))
                .play(t -> { t.hold(net.minecraft.world.item.Items.FLINT_AND_STEEL); for (int i = 0; i < t.count("lamp"); i++) t.use("lamp", i); })
                .add();
    }
}
