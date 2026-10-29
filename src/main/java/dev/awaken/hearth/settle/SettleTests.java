package dev.awaken.hearth.settle;

import com.mojang.serialization.JsonOps;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.Comfort;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.TierRules;
import dev.awaken.hearth.block.HearthAnchorBlock;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.Reading;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.room.Tally;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.item.DyeColor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * GameTests for W6 (SPEC §28.8), one per mechanism: an older world's settlers move to the city (SPEC §43.6) with their
 * work in hand finished; the tiers read the home (four rooms with a Fine one and a field, a Grand room, a bell tower and
 * six rooms, ten with two Grand) and the Anchor's flame follows the tier; the Ore Shed is a room with a lode heart and a barrel.
 */
public final class SettleTests {
    private SettleTests() {}

    public static void register() {
        // (SPEC §43.6) nobody lives at a Hearth: an older world's settlers move to the city, their work in hand finished first
        AwakenTests.register("hearth_people_moved_to_city", 100, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            HearthData data = HearthData.get(level.getServer());
            HearthRecord rec = new HearthRecord(sp.getUUID(), "test", h.absolutePos(new BlockPos(1, 1, 1)), HearthService.OVERWORLD);
            data.put(rec);
            try {
                rec.folk.settlers.add(new Folk.Settler(Settlers.forceKnow(level, rec, "oren"), null, Folk.Settler.HOME, 0));
                rec.folk.commissions.add(new Folk.Commission(sp.getUUID(), "minecraft:iron_sword", 1, Long.MAX_VALUE - 1));
                Settlers.upkeep(level, rec, level.getGameTime());
                h.assertTrue(rec.folk.settlers.isEmpty() && rec.folk.known.isEmpty(), "nobody lives here now");
                h.assertTrue(rec.folk.commissions.isEmpty() && rec.folk.stash.isEmpty(), "Oren's piece was finished, not lost");
                boolean left = !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(rec.pos).inflate(3),
                        e -> e.getItem().is(net.minecraft.world.item.Items.IRON_SWORD)).isEmpty();
                h.assertTrue(left, "with no chest at home, it's set down by the fire");
                h.assertTrue(dev.awaken.hearth.guide.Cottage.state(sp, rec) == dev.awaken.hearth.guide.GuideData.COTTAGE_NONE, "nobody offers a cottage");
                h.assertTrue(TierRules.needs(rec, 4).stream().noneMatch(n -> n.text().contains("people")), "no tier asks for people");
                for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(rec.pos).inflate(3))) e.discard();
                h.succeed();
            } finally {
                data.remove(sp.getUUID());
            }
        });

        AwakenTests.registerIsolated("hearth_tier_reading", 400, h -> {
            ServerLevel level = h.getLevel();
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            BlockPos at = new BlockPos((origin.getX() & ~15) + 8, origin.getY() + 100, (origin.getZ() & ~15) + 8);
            UUID owner = UUID.randomUUID();
            HearthRecord rec = new HearthRecord(owner, "test", at, HearthService.OVERWORLD);
            try {
                // II: four rooms, one Fine, a field or a pen
                rec.rooms = List.of(fake(Reading.Grade.SNUG, RoomType.BEDROOM), fake(Reading.Grade.SNUG, RoomType.KITCHEN),
                        fake(Reading.Grade.BARE, RoomType.SHELTER));
                h.assertFalse(TierRules.met(rec, 2), "three rooms aren't a Steading");
                rec.rooms = List.of(fake(Reading.Grade.SNUG, RoomType.BEDROOM), fake(Reading.Grade.SNUG, RoomType.KITCHEN),
                        fake(Reading.Grade.BARE, RoomType.SHELTER), fake(Reading.Grade.FINE, RoomType.ARMORY));
                h.assertFalse(TierRules.met(rec, 2), "and four with a Fine one want a field or a pen");
                rec.land = Comfort.FIELD;
                h.assertTrue(TierRules.met(rec, 2), "four rooms, one Fine, a field: a Steading");
                // III: a Grand room
                h.assertFalse(TierRules.met(rec, 3), "no Grand room yet");
                rec.rooms = List.of(fake(Reading.Grade.GRAND, RoomType.HALL), fake(Reading.Grade.SNUG, RoomType.BEDROOM));
                h.assertTrue(TierRules.met(rec, 3), "a Grand room: a Hall");
                // IV: a bell tower and six rooms; V: ten rooms, two Grand (SPEC §43.6: nobody moves in)
                List<Room> five = new java.util.ArrayList<>(List.of(fake(Reading.Grade.GRAND, RoomType.HALL), fake(Reading.Grade.SNUG, RoomType.BELL_TOWER)));
                for (int i = 0; i < 3; i++) five.add(fake(Reading.Grade.SNUG, RoomType.BEDROOM));
                rec.rooms = List.copyOf(five);
                h.assertFalse(TierRules.met(rec, 4), "five rooms aren't a village");
                five.add(fake(Reading.Grade.SNUG, RoomType.KITCHEN));
                rec.rooms = List.copyOf(five);
                h.assertTrue(TierRules.met(rec, 4), "a bell tower and six rooms: a Village");
                for (int i = 0; i < 4; i++) five.add(fake(Reading.Grade.SNUG, RoomType.BEDROOM));
                rec.rooms = List.copyOf(five);
                h.assertFalse(TierRules.met(rec, 5), "ten rooms with one Grand aren't a town");
                five.add(fake(Reading.Grade.GRAND, RoomType.SANCTUARY));
                rec.rooms = List.copyOf(five);
                h.assertTrue(TierRules.met(rec, 5), "ten rooms, two Grand: a Town");
                h.assertTrue(HearthRecord.radiusForTier(4) == 80 && HearthRecord.radiusForTier(5) == 96, "Village 80, Town 96");
                // the Anchor's flame follows the tier
                level.setBlock(at, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
                rec.tier = 4;
                HearthService.showTier(level, rec);
                BlockState s = level.getBlockState(at);
                h.assertTrue(s.is(HearthContent.HEARTH_ANCHOR.get()) && s.getValue(HearthAnchorBlock.TIER) == 4, "the Anchor burns at Tier IV");
                h.assertTrue(TierRules.village(rec) && TierRules.name(4).equals("Village"), "Tier IV is a village");
                h.succeed();
            } finally {
                level.removeBlock(at, false);
            }
        });

        // (SPEC §43.6) the Mine head became the Ore Shed: a closed room with a lode heart and a barrel
        AwakenTests.registerIsolated("hearth_ore_shed", 600, h -> {
            ServerLevel level = h.getLevel();
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            BlockPos base = new BlockPos((origin.getX() & ~15) + 4, origin.getY() + 120, (origin.getZ() & ~15) + 4);
            HearthData data = HearthData.get(level.getServer());
            UUID owner = UUID.randomUUID();
            HearthRecord rec = new HearthRecord(owner, "test", base.offset(3, 8, 3), HearthService.OVERWORLD);
            level.setBlock(rec.pos, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
            data.put(rec);
            try {
                // a 5 x 5 shed (3 x 3 inside, 3 high) with a lantern, a lode heart and a barrel
                for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++) for (int z = 0; z < 5; z++) {
                    boolean shell = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
                    level.setBlock(base.offset(x, y, z), shell ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(base.offset(1, 3, 1), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), 3);
                level.setBlock(base.offset(2, 1, 2), dev.awaken.delve.DelveContent.HEARTS.get(dev.awaken.delve.Ore.IRON).get().defaultBlockState(), 3);
                level.setBlock(base.offset(3, 1, 3), Blocks.BARREL.defaultBlockState(), 3);
                AwakenTests.awaitLight(h, base.offset(-1, -1, -1), base.offset(5, 5, 5));
                HearthService.scanNow(level, rec);
                boolean shed = false;
                for (Room r : rec.rooms) shed |= r.types().contains(RoomType.MINE_HEAD);
                h.assertTrue(shed, "a closed room with a lode heart and a barrel is an Ore Shed (rooms: " + kinds(rec) + ")");
                h.assertTrue(RoomType.MINE_HEAD.label.equals("Ore Shed"), "it's called the Ore Shed");
                h.succeed();
            } finally {
                data.remove(owner);
                level.removeBlock(rec.pos, false);
                clear(level, base, 5, 5, 5);
            }
        });
    }

    /** A 7 x 3 x 7 timber bedroom like HomeTests' (lit, a window, a bed, soft things): Fine. */
    static void room(ServerLevel level, BlockPos base) {
        for (int x = 0; x < 9; x++) for (int y = 0; y < 5; y++) for (int z = 0; z < 9; z++) {
            boolean shell = x == 0 || x == 8 || y == 0 || y == 4 || z == 0 || z == 8;
            BlockState s = !shell ? Blocks.AIR.defaultBlockState() : y == 4 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
            level.setBlock(base.offset(x, y, z), s, 3);
        }
        level.setBlock(base.offset(0, 2, 4), Blocks.GLASS_PANE.defaultBlockState(), 3);
        level.setBlock(base.offset(4, 4, 4), Blocks.OAK_TRAPDOOR.defaultBlockState(), 3);
        BlockState foot = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
        level.setBlock(base.offset(2, 1, 1), foot, 3);
        level.setBlock(base.offset(2, 1, 2), foot.setValue(BedBlock.PART, BedPart.HEAD), 3);
        level.setBlock(base.offset(4, 1, 4), Blocks.LANTERN.defaultBlockState(), 3);
        level.setBlock(base.offset(6, 1, 6), Blocks.LANTERN.defaultBlockState(), 3);
        level.setBlock(base.offset(5, 1, 2), Blocks.CARPET.pick(DyeColor.RED).defaultBlockState(), 3);
        level.setBlock(base.offset(6, 1, 1), Blocks.POTTED_POPPY.defaultBlockState(), 3);
        level.setBlock(base.offset(2, 1, 6), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH), 3);
        level.setBlock(base.offset(7, 1, 3), Blocks.CAMPFIRE.defaultBlockState(), 3);
    }

    static void shaft(ServerLevel level, BlockPos hatch, int depth) {
        for (int k = 1; k <= depth; k++) {
            BlockPos c = hatch.below(k);
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dz != 0) level.setBlock(c.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
            }
            level.setBlock(c, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH), 3);
        }
        level.setBlock(hatch.below(depth + 1), Blocks.STONE.defaultBlockState(), 3);
    }

    static Room fake(Reading.Grade g, RoomType type) {
        EnumSet<RoomType> types = EnumSet.of(RoomType.SHELTER, type);
        int v = g.ordinal();
        Reading reading = new Reading(v, v, v, v, v, v, g, "the " + g.word + " room", "", type);
        return new Room(types, BlockPos.ZERO, BlockPos.ZERO, new LongOpenHashSet(), new int[Furniture.COUNT], new int[Tally.F_COUNT], Set.of(), reading);
    }

    static boolean has(List<ItemStack> l, net.minecraft.world.item.Item item) {
        for (ItemStack s : l) if (s.is(item)) return true;
        return false;
    }

    static String kinds(HearthRecord h) {
        StringBuilder b = new StringBuilder();
        for (Room r : h.rooms) b.append(r.types()).append(' ');
        return b.toString();
    }

    static void clear(ServerLevel level, BlockPos base, int w, int hgt, int d) {
        for (int x = 0; x < w; x++) for (int y = 0; y < hgt; y++) for (int z = 0; z < d; z++) {
            level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
