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

/** Wisps (follow): things that move off when you come near and wait when you fall behind. */
final class Wisps {
    private Wisps() {}

    static void init() {
        Kind.of(FSet.WISPS, 1, "marsh_light", "The Marsh Light").verb(Verb.FOLLOW).curio().rank(0).lands(Group.SWAMP, Group.PLAINS, Group.FOREST, Group.DARK).anchor(Anchor.FLAT).made(Made.STRANGE)
                .see("A small light over the ground after dusk, which moves off when you come near.")
                .learned("It waits when you fall behind and takes the dry way. Where it stopped, something was buried.")
                .reach(30).slope(4).tell(Tell.FIREFLIES).icon(() -> net.minecraft.world.item.Items.GLOWSTONE_DUST)
                .scene(s -> {
                    int x = 0, z = 0;
                    double a = s.rnd.nextDouble() * 6.283;
                    s.partOn("way", 0, 0, 0);
                    for (int i = 1; i <= 6; i++) {
                        a += (s.rnd.nextDouble() - 0.5) * 1.4;
                        int nx = x + (int) Math.round(Math.cos(a) * 4.5), nz = z + (int) Math.round(Math.sin(a) * 4.5);
                        for (int t = 0; t < 5 && s.depth(nx, nz) > 0; t++) {
                            a += 1.0;
                            nx = x + (int) Math.round(Math.cos(a) * 4.5);
                            nz = z + (int) Math.round(Math.sin(a) * 4.5);
                        }
                        x = nx;
                        z = nz;
                        s.partOn("way", x, z, 0);
                    }
                    s.floor(x, z, Blocks.COARSE_DIRT.defaultBlockState());
                    s.part("spot", x, s.g(x, z), z);
                    s.part("cache", x, s.g(x, z) + 1, z);
                    Dress.scatter(s, x, z, 2, 3, () -> Dress.pebbles(s));
                })
                .script(() -> Steps.of(When.only(When.NIGHT.or(When.DUSK), Guide.along("way")), Dig.at("spot")))
                .play(t -> { t.hour(18000); for (int i = 0; i < t.count("way"); i++) t.stand("way", i).ticks(140); t.dig("spot", 0); })
                .add();
    }
}
