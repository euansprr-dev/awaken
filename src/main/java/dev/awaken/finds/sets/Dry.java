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

/** Dry Things (pour): what a little water, or a little heat, brings back. */
final class Dry {
    private Dry() {}

    /** A few birds come down, the way they do to water. */
    static void birds(Ctx c, int n) {
        for (int i = 0; i < n; i++) {
            var e = net.minecraft.world.entity.EntityTypes.PARROT.create(c.level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (e == null) continue;
            var at = c.site.ground();
            e.snapTo(at.getX() + 0.5 + c.level.getRandom().nextInt(5) - 2, at.getY() + 4 + i, at.getZ() + 0.5 + c.level.getRandom().nextInt(5) - 2, c.level.getRandom().nextFloat() * 360, 0);
            c.level.addFreshEntity(e);
        }
    }

    static void init() {
        Kind.of(FSet.DRY, 1, "dry_basin", "The Dry Basin").verb(Verb.POUR).curio().rank(0).lands(Group.PLAINS, Group.DESERT, Group.BADLANDS, Group.FOREST).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A stone basin on a block of rock, bone dry, with dead brush round it.")
                .learned("Filled, it brought the birds down first. Then something had left a thing in the bottom.")
                .reach(6).icon(Props.STONE_BASIN)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(4);
                    s.on(0, 0, m.stone());
                    s.partOn("basin", 0, 0, 1, Props.STONE_BASIN.get().of(0));
                    s.on(1, 0, m.slab());
                    s.partOn("cache", 0, 1, 0);
                    s.on(-2, 1, Blocks.DEAD_BUSH.defaultBlockState());
                    s.on(2, -2, Blocks.DEAD_BUSH.defaultBlockState());
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Pour.water("basin", 2))
                .after(c -> birds(c, 3))
                .play(t -> t.hold(net.minecraft.world.item.Items.WATER_BUCKET).use("basin", 0).hold(net.minecraft.world.item.Items.WATER_BUCKET).use("basin", 0))
                .add();
    }
}
