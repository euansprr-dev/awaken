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

/** Sealed Things (break): shut things that open to the right blow, heat or sound. */
final class Sealed {
    private Sealed() {}

    static void init() {
        Kind.of(FSet.SEALED, 1, "cracked_boulder", "The Cracked Boulder").verb(Verb.BREAK).curio().rank(0).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A boulder with a crack through it, and something glinting inside.")
                .learned("A pick opened it along the crack. It was hollow, and lined.")
                .reach(5).tell(Tell.GLINT).icon(() -> net.minecraft.world.item.Items.AMETHYST_SHARD)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    for (int x = -1; x <= 1; x++)
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && z == 0) continue;
                            s.on(x, z, s.rnd.nextInt(3) == 0 ? m.mossy() : m.stone());
                            if (Math.abs(x) + Math.abs(z) == 1) {
                                BlockState st = m.stairs();
                                Direction in = x == 1 ? Direction.WEST : x == -1 ? Direction.EAST : z == 1 ? Direction.NORTH : Direction.SOUTH;
                                s.up(x, z, 1, st.hasProperty(StairBlock.FACING) ? st.setValue(StairBlock.FACING, in) : st);
                            }
                        }
                    s.up(0, 0, 1, m.slab());
                    s.on(0, 0, Blocks.AMETHYST_CLUSTER.defaultBlockState());
                    s.partOn("cache", 0, 0, 0);
                    s.partOn("seal", 0, 1, 0, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
                    s.tell(0, 1, 1);
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Seal.opens("seal", 3, Hands::pick))
                .play(t -> t.hold(net.minecraft.world.item.Items.IRON_PICKAXE).hit("seal", 0).hit("seal", 0).hit("seal", 0))
                .add();
    }
}
