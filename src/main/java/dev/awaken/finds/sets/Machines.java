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

/** Old Machines (turn): things that still work if you set them right. */
final class Machines {
    private Machines() {}

    private static final int[] MX = {-3, -2, 0, 2, 3}, MZ = {0, -2, -3, -2, 0};

    /** The sundial: straighten the pointer, then press the stone its shadow lies on. The shadow moves through the day. */
    private static final class Sundial implements Script {
        private int under(Ctx c) {
            return c.night() ? -1 : (int) Math.min(4, c.hour() * 5 / 12000);
        }

        @Override
        public void tick(Ctx c) {
            if (c.at("dial", 0).getValue(dev.awaken.finds.kit.PropBlock.STATE) == 0) return;
            int u = under(c);
            for (int i = 0; i < c.count("mark"); i++) {
                BlockState want = Props.START_STONE.get().of(i == u ? 3 : 0);
                if (c.at("mark", i) != want) c.set("mark", i, want);
            }
        }

        @Override
        public boolean use(Ctx c, net.minecraft.server.level.ServerPlayer p, String part, int i, net.minecraft.world.item.ItemStack held) {
            if (part.equals("dial")) {
                BlockState d = c.at("dial", 0);
                if (d.getValue(dev.awaken.finds.kit.PropBlock.STATE) == 0) {
                    c.set("dial", 0, d.setValue(dev.awaken.finds.kit.PropBlock.STATE, 1));
                    c.sound(c.pos("dial", 0), net.minecraft.sounds.SoundEvents.COPPER_HIT, 0.9F, 0.8F);
                }
                return true;
            }
            if (!part.equals("mark") || c.at("dial", 0).getValue(dev.awaken.finds.kit.PropBlock.STATE) == 0) return false;
            if (i == under(c)) c.complete(p);
            else c.sound(c.pos("mark", i), net.minecraft.sounds.SoundEvents.STONE_HIT, 0.6F, 0.6F);
            return true;
        }
    }

    static void init() {
        Kind.of(FSet.MACHINES, 1, "sundial", "The Sundial").verb(Verb.TURN).curio().rank(0).lands(Group.PLAINS, Group.DESERT, Group.FOREST, Group.BADLANDS).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A stone sundial with its pointer bent flat, and a half ring of worn stones round it.")
                .learned("Straightened, its shadow walks the stones through the day. The one it rests on gives.")
                .reach(6).icon(Props.SUNDIAL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    s.partOn("dial", 0, 0, 0, Props.SUNDIAL.get().of(0));
                    for (int i = 0; i < 5; i++) s.partOn("mark", MX[i], MZ[i], 0, Props.START_STONE.get().of(0));
                    s.partOn("cache", 0, 1, 0);
                    Dress.skirt(s, 6, m);
                })
                .script(Sundial::new)
                .play(t -> t.hour(6000).use("dial", 0).ticks(1).use("mark", 2))
                .add();
    }
}
