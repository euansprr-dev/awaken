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

/** Runs (run): courses against a clock, for whatever moves you have. */
final class Runs {
    private Runs() {}

    static void init() {
        Kind.of(FSet.RUNS, 1, "lantern_run", "The Lantern Run").verb(Verb.RUN).curio().rank(0).anchor(Anchor.LINE).length(66).made(Made.BUILT)
                .see("A flat stone with a mark cut in it, and a row of small lanterns going off across country.")
                .learned("Touch the stone and they light in a chain. You reached the last before the first went out.")
                .reach(34).icon(Props.START_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.partOn("start", 0, -30, 0, Props.START_STONE.get().of(1));
                    int x = 0;
                    for (int i = 0; i < 6; i++) {
                        int z = -20 + i * 10;
                        x += s.rnd.nextInt(5) - 2;
                        x = Math.max(-4, Math.min(4, x));
                        s.on(x, z, Props.POST.get().of(0));
                        s.partOn("gate", x, z, 1, Props.IRON_LAMP.get().of(0));
                        if (i == 5) s.partOn("cache", x + 1, z, 0, Props.FOOTING.get().front(0, Direction.NORTH));
                    }
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Course.run("start", "gate", 16))
                .play(t -> { t.use("start", 0); for (int i = 0; i < t.count("gate"); i++) t.stand("gate", i).ticks(1); })
                .add();
    }
}
